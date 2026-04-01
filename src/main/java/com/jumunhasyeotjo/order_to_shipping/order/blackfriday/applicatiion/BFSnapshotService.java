package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.jumunhasyeotjo.order_to_shipping.common.exception.BusinessException;
import com.jumunhasyeotjo.order_to_shipping.common.exception.ErrorCode;
import com.jumunhasyeotjo.order_to_shipping.common.tracing.TracedAsync;
import com.jumunhasyeotjo.order_to_shipping.coupon.presentation.dto.res.CouponRes;
import com.jumunhasyeotjo.order_to_shipping.order.application.CreateOrderSnapshotDto;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.CreateOrderCommand;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.OrderProductReq;
import com.jumunhasyeotjo.order_to_shipping.order.application.dto.ProductResult;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderCouponClient;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderProductClient;
import com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion.dto.OrderPreContextDto;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
public class BFSnapshotService {

    private final BFOrderValidService validService;
    private final BFOrderService bfOrderService;
    private final OrderProductClient orderProductClient;
    private final OrderCouponClient orderCouponClient;
    private final Executor ioExecutor;

    public BFSnapshotService(
            BFOrderValidService validService,
            BFOrderService bfOrderService,
            OrderProductClient orderProductClient,
            OrderCouponClient orderCouponClient,
            @Qualifier("ioExecutor") Executor ioExecutor
    ) {
        this.validService = validService;
        this.bfOrderService = bfOrderService;
        this.orderProductClient = orderProductClient;
        this.orderCouponClient = orderCouponClient;
        this.ioExecutor = ioExecutor;
    }

    @Value("${order.blackfriday.prevalidation-timeout:3s}")
    private Duration prevalidationTimeout;

    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("order-service");
    /**
     * [Tx-1] Request SnapShot 저장
     * - 별도 트랜잭션으로 분리
     * - Order 엔티티 PENDING 상태로 저장
     * - 즉시 커밋
     */
    @Transactional
    public CreateOrderSnapshotDto createOrderSnapshot(CreateOrderCommand command) {
        log.debug("[Tx-1 Start] Request SnapShot 저장 시작");

        // 1. 검증 및 필요한 데이터 조회
        OrderPreContextDto preContext = preValidateAndLoadOrderContext(command);

        // 2. pendingOrder 생성
        Order pendingOrder = bfOrderService.createOrderAggregate(command, preContext.productResultList());
        log.debug("[Tx-1 End] Order 저장 완료 - orderId: {}", pendingOrder.getId());

        // Tx-1 커밋 (메서드 종료 시)
        Integer discountPrice = preContext.couponRes() != null
                ? preContext.couponRes().discountAmount()
                : null;
        return new CreateOrderSnapshotDto(pendingOrder, discountPrice);
    }


    private OrderPreContextDto preValidateAndLoadOrderContext(CreateOrderCommand command) {
        Context parentContext = Context.current();
        Executor contextAwareExecutor = Context.taskWrapping(ioExecutor);
        UUID couponId = command.couponId();
        CompletableFuture<Void> validateCompanyFuture = CompletableFuture.runAsync(
                () -> TracedAsync.runTraced(
                        "order.prevalidate.validateCompany",
                        parentContext,
                        tracer,
                        () -> validService.validateCompany(command.organizationId())
                ),
                contextAwareExecutor
        );
        CompletableFuture<Void> validateDuplicateOrderFuture = CompletableFuture.runAsync(
                () -> TracedAsync.runTraced(
                        "order.prevalidate.validateDuplicateOrder",
                        parentContext,
                        tracer,
                        () -> validService.validateDuplicateOrder(command.idempotencyKey())
                ),
                contextAwareExecutor
        );
        CompletableFuture<List<ProductResult>> productLookupFuture = CompletableFuture.supplyAsync(
                () -> TracedAsync.supplyTraced(
                        "order.prevalidate.findAllOrderProduct",
                        parentContext,
                        tracer,
                        () -> findAllOrderProduct(command.orderProducts())
                ),
                contextAwareExecutor
        );

        CompletableFuture<CouponRes> couponLookupFuture = null;
        if (couponId != null) {
            CompletableFuture<Void> validateCouponFuture = CompletableFuture.runAsync(
                    () -> TracedAsync.runTraced(
                            "order.prevalidate.validateCoupon",
                            parentContext,
                            tracer,
                            () -> validService.validateCoupon(command.userId(), couponId)
                    ),
                    contextAwareExecutor
            );
            couponLookupFuture = CompletableFuture.supplyAsync(
                    () -> TracedAsync.supplyTraced(
                            "order.prevalidate.findCoupon",
                            parentContext,
                            tracer,
                            () -> findCoupon(couponId)
                    ),
                    contextAwareExecutor
            );

            awaitAll(validateCompanyFuture, validateDuplicateOrderFuture, validateCouponFuture, productLookupFuture, couponLookupFuture);
        } else {
            awaitAll(validateCompanyFuture, validateDuplicateOrderFuture, productLookupFuture);
        }

        CouponRes couponRes = couponLookupFuture != null ? couponLookupFuture.join() : null;
        return new OrderPreContextDto(productLookupFuture.join(), couponRes);
    }

    private void awaitAll(CompletableFuture<?>... futures) {
        try {
            CompletableFuture.allOf(futures)
                    .get(prevalidationTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw wrapValidationFailure(e);
        } catch (TimeoutException e) {
            throw wrapValidationFailure(e);
        } catch (ExecutionException e) {
            Throwable cause = unwrapThrowable(e);
            if (cause instanceof BusinessException businessException) {
                throw businessException;
            }
            throw wrapValidationFailure(cause);
        }
    }

    private Throwable unwrapThrowable(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof ExecutionException
                || current instanceof java.util.concurrent.CompletionException) {
            if (current.getCause() == null) {
                break;
            }
            current = current.getCause();
        }
        return current;
    }

    private BusinessException wrapValidationFailure(Throwable cause) {
        log.warn("[BF Prevalidation] failed - cause: {}", cause);
        BusinessException wrapped = new BusinessException(ErrorCode.ORDER_VALIDATION_FAILED);
        if (cause != null) {
            wrapped.initCause(cause);
        }
        return wrapped;
    }

    private CouponRes findCoupon(UUID couponId) {
        return orderCouponClient.findCoupon(couponId);
    }

    private List<ProductResult> findAllOrderProduct(List<OrderProductReq> orderProducts) {
        List<ProductResult> allProducts = orderProductClient
                .findAllProducts(orderProducts.stream()
                        .map(OrderProductReq::productId)
                        .toList())
                .data();

        if (allProducts.size() != orderProducts.size()) {
            log.error("[검증 실패] 상품 정보 불일치 - 요청: {}, 조회: {}",
                    orderProducts.size(), allProducts.size());
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        return allProducts;
    }
}
