package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.jumunhasyeotjo.order_to_shipping.common.exception.BusinessException;
import com.jumunhasyeotjo.order_to_shipping.common.exception.ErrorCode;
import com.jumunhasyeotjo.order_to_shipping.order.application.CreateOrderSnapshotDto;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.CreateOrderCommand;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.OrderProductReq;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderCouponClient;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderStockClient;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.RollbackPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class BFOrderOrchestrator {
    private final BFOrderService bfOrderService;
    private final BFSnapshotService bfSnapshotService;
    private final BFOrderWithdrawService bfOrderWithdrawService;
    private final OrderCouponClient orderCouponClient;
    private final OrderStockClient orderStockClient;
    private final Executor ioExecutor;

    public BFOrderOrchestrator(
            BFOrderService bfOrderService,
            BFSnapshotService bfSnapshotService,
            BFOrderWithdrawService bfOrderWithdrawService,
            OrderCouponClient orderCouponClient,
            OrderStockClient orderStockClient,
            @Qualifier("ioExecutor") Executor ioExecutor
    ) {
        this.bfOrderService = bfOrderService;
        this.bfSnapshotService = bfSnapshotService;
        this.bfOrderWithdrawService = bfOrderWithdrawService;
        this.orderCouponClient = orderCouponClient;
        this.orderStockClient = orderStockClient;
        this.ioExecutor = ioExecutor;
    }

    /**
     * 주문 생성 전체 프로세스
     *
     * 흐름:
     * 1. 사전 검증 및 상품 조회
     * 2. PENDING 주문 생성
     * 3. 쿠폰 사용 + 재고 차감 API 호출
     * 4. 결제 승인
     * 5. 주문 상태 확정 (ORDERED)
     * 6. 사용자 응답 반환
     */
    public Order createOrder(CreateOrderCommand command) {
        log.debug("[주문 생성 시작] idempotencyKey: {}", command.idempotencyKey());
        // 1. [Tx-1] Request SnapShot 저장 - 별도 트랜잭션
        CreateOrderSnapshotDto snapshotDto = bfSnapshotService.createOrderSnapshot(command);
        Order pendingOrder = snapshotDto.getPendingOrder();
        log.debug("[PENDING 저장 완료] orderId: {}, status: PENDING", pendingOrder.getId());

        int paymentPrice = pendingOrder.getTotalPrice();
        CompletableFuture<Integer> couponFuture = startCoupon(command.couponId(), pendingOrder.getId());
        CompletableFuture<Void> stockFuture = startStock(command.orderProducts(), pendingOrder.getId());
        CompletableFuture<PreparationResult> couponResultFuture = couponFuture.handle(
                (discountPrice, throwable) -> PreparationResult.coupon(
                        discountPrice,
                        throwable == null ? null : unwrapThrowable(throwable)
                )
        );
        CompletableFuture<PreparationResult> stockResultFuture = stockFuture.handle(
                (ignored, throwable) -> PreparationResult.stock(
                        throwable == null ? null : unwrapThrowable(throwable)
                )
        );

        try {
            int discountPrice = awaitPreparation(
                    couponFuture,
                    stockFuture,
                    couponResultFuture,
                    stockResultFuture,
                    pendingOrder.getId(),
                    command.couponId() != null
            );
            paymentPrice -= discountPrice;
            bfOrderWithdrawService.withdraw(pendingOrder, paymentPrice, command);
            log.debug("[Final Stage 완료] 주문 완료 - orderId: {}, status: ORDERED", pendingOrder.getId());

            return bfOrderService.updateStatusForComplete(pendingOrder.getId(), command);

        } catch (RuntimeException e) {
            if (e instanceof PreparationFailedException preparationFailedException) {
                Throwable cause = unwrapThrowable(preparationFailedException.getCause());
                log.error("[주문 생성 실패] orderId: {}, error: {}", pendingOrder.getId(), cause.getMessage(), cause);
                throw asRuntimeException(cause);
            }
            Throwable cause = unwrapThrowable(e);
            log.error("[주문 생성 실패] orderId: {}, error: {}", pendingOrder.getId(), cause.getMessage(), cause);
            bfOrderService.updateStatusForRollback(
                    pendingOrder.getId(),
                    RollbackPlan.paymentFailure(command.couponId() != null)
            );
            throw asRuntimeException(cause);
        }
    }

    private CompletableFuture<Integer> startCoupon(UUID couponId, UUID orderId) {
        if (couponId == null) {
            return CompletableFuture.completedFuture(0);
        }
        return CompletableFuture.supplyAsync(() -> useCoupon(couponId, orderId), ioExecutor);
    }

    private CompletableFuture<Void> startStock(List<OrderProductReq> orderProducts, UUID orderId) {
        return CompletableFuture.runAsync(() -> decreaseStock(orderProducts, orderId), ioExecutor);
    }

    private Integer useCoupon(UUID couponId, UUID orderId) {
        Integer discountPrice = orderCouponClient.useCoupon(couponId, orderId);
        if (discountPrice == null || discountPrice == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return discountPrice;
    }

    private void decreaseStock(List<OrderProductReq> orderProducts, UUID orderId) {
        if (!orderStockClient.decreaseStock(orderProducts, orderId.toString()).data()) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_STOCK);
        }
    }

    private int awaitPreparation(
            CompletableFuture<Integer> couponFuture,
            CompletableFuture<Void> stockFuture,
            CompletableFuture<PreparationResult> couponResultFuture,
            CompletableFuture<PreparationResult> stockResultFuture,
            UUID orderId,
            boolean compensateCoupon
    ) {
        PreparationResult firstCompleted = (PreparationResult) CompletableFuture.anyOf(couponResultFuture, stockResultFuture).join();
        if (firstCompleted.failed()) {
            throwPreparationFailure(firstCompleted, orderId, compensateCoupon, couponFuture, stockFuture);
        }
        PreparationResult couponResult = couponResultFuture.join();
        if (couponResult.failed()) {
            throwPreparationFailure(couponResult, orderId, compensateCoupon, couponFuture, stockFuture);
        }
        PreparationResult stockResult = stockResultFuture.join();
        if (stockResult.failed()) {
            throwPreparationFailure(stockResult, orderId, compensateCoupon, couponFuture, stockFuture);
        }
        log.debug("[쿠폰/재고 준비 완료] orderId: {}, discountPrice: {}", orderId, couponResult.discountPrice());
        return couponResult.discountPrice();
    }

    private void throwPreparationFailure(
            PreparationResult result,
            UUID orderId,
            boolean compensateCoupon,
            CompletableFuture<Integer> couponFuture,
            CompletableFuture<Void> stockFuture
    ) {
        couponFuture.cancel(true);
        stockFuture.cancel(true);
        if (result.step() == PreparationStep.COUPON) {
            bfOrderService.updateStatusForRollback(orderId, RollbackPlan.useCouponFailure());
        } else {
            bfOrderService.updateStatusForRollback(orderId, RollbackPlan.decreaseStockFailure(compensateCoupon));
        }
        throw new PreparationFailedException(result.throwable());
    }

    private Throwable unwrapThrowable(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private RuntimeException asRuntimeException(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(throwable);
    }

    private enum PreparationStep {
        COUPON,
        STOCK
    }

    private record PreparationResult(PreparationStep step, int discountPrice, Throwable throwable) {
        private static PreparationResult coupon(Integer discountPrice, Throwable throwable) {
            return new PreparationResult(PreparationStep.COUPON, discountPrice == null ? 0 : discountPrice, throwable);
        }

        private static PreparationResult stock(Throwable throwable) {
            return new PreparationResult(PreparationStep.STOCK, 0, throwable);
        }

        private boolean failed() {
            return throwable != null;
        }
    }

    private static class PreparationFailedException extends RuntimeException {
        private PreparationFailedException(Throwable cause) {
            super(cause);
        }
    }
}
