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
import com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion.outbox.BFOrderOutboxService;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


@Service
@RequiredArgsConstructor
@Slf4j
public class BFSnapshotService {

    private final BFOrderOutboxService bfOrderOutboxService;
    private final BFOrderValidService validService;
    private final BFOrderService bfOrderService;
    private final OrderProductClient orderProductClient;
    private final OrderCouponClient orderCouponClient;
    private final Executor ioExecutor;  // Virtual Thread Executor
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

        // 3. outbox 초기화 -> 메인 thread와 redis outbox worker thread의 순서 보장이 어려워 미리 생성
        bfOrderOutboxService.createOutbox(
                pendingOrder.getId(),
                pendingOrder.getId().toString(),
                "BF_ORDER_CREATED",
                null);

        log.debug("[Tx-1 End] Order 저장 완료 - orderId: {}", pendingOrder.getId());

        // Tx-1 커밋 (메서드 종료 시)
        return new CreateOrderSnapshotDto(pendingOrder, preContext.couponRes().discountAmount());
    }


    private OrderPreContextDto preValidateAndLoadOrderContext(CreateOrderCommand command) {
        Context parentContext = Context.current();
        Executor contextAwareExecutor = Context.taskWrapping(ioExecutor);
        UUID organizationId = command.organizationId();
        String idempotencyKey = command.idempotencyKey();
        Long userId = command.userId();
        UUID couponId = command.couponId();
        List<OrderProductReq> orderProductReqs = command.orderProducts();

        var validCompany = CompletableFuture.runAsync(() -> {
            String spanName = "order.prevalidate.validateCompany";
            TracedAsync.runTraced(spanName, parentContext, tracer,
                    () -> validService.validateCompany(organizationId)
            );
        }, contextAwareExecutor);

        var validOrder = CompletableFuture.runAsync(() -> {
            String spanName = "order.prevalidate.validateDuplicateOrder";
            TracedAsync.runTraced(spanName, parentContext, tracer,
                    () -> validService.validateDuplicateOrder(idempotencyKey)
            );
        }, contextAwareExecutor);

        var validCoupon = CompletableFuture.runAsync(() -> {
            String spanName = "order.prevalidate.validateCoupon";
            TracedAsync.runTraced(spanName, parentContext, tracer,
                    () -> validService.validateCoupon(userId, couponId)
            );
        }, contextAwareExecutor);

        var findCoupon = CompletableFuture.supplyAsync(() -> {
            String spanName = "order.prevalidate.findCoupon";
            return TracedAsync.supplyTraced(spanName, parentContext, tracer,
                    ()-> findCoupon(couponId)
            );
        }, contextAwareExecutor);

        var allOrderProduct = CompletableFuture.supplyAsync(() -> {
            String spanName = "order.prevalidate.findAllOrderProduct";
            return TracedAsync.supplyTraced(spanName, parentContext, tracer,
                    () -> findAllOrderProduct(orderProductReqs)
            );
        }, contextAwareExecutor);

        CompletableFuture
                .allOf(validCoupon, validCompany, validOrder, findCoupon, allOrderProduct)
                .join();

        return new OrderPreContextDto(allOrderProduct.join(), findCoupon.join());
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