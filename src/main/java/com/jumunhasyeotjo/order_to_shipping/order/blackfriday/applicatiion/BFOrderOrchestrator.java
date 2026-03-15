package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.jumunhasyeotjo.order_to_shipping.common.exception.BusinessException;
import com.jumunhasyeotjo.order_to_shipping.common.exception.ErrorCode;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.CreateOrderCommand;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.OrderProductReq;
import com.jumunhasyeotjo.order_to_shipping.order.application.dto.ProductResult;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderCouponClient;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderProductClient;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderStockClient;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.RollbackStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BFOrderOrchestrator {
    private final BFOrderValidService bfOrderValidService;
    private final BFOrderService bfOrderService;
    private final BfOrderWithdrawService bfOrderWithdrawService;
    private final OrderProductClient orderProductClient;
    private final OrderCouponClient orderCouponClient;
    private final OrderStockClient orderStockClient;

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
        validateAndLoadContext(command);
        List<ProductResult> productResults = findAllOrderProduct(command.orderProducts());
        Order pendingOrder = bfOrderService.createOrderAggregate(command, productResults);
        log.debug("[PENDING 저장 완료] orderId: {}, status: PENDING", pendingOrder.getId());

        RollbackStatus status = RollbackStatus.NONE;
        int paymentPrice = pendingOrder.getTotalPrice();

        try {
            if (command.couponId() != null) {
                status = RollbackStatus.USE_COUPON;
                int discountPrice = useCoupon(command.couponId(), pendingOrder.getId());
                paymentPrice -= discountPrice;
                log.debug("[쿠폰 사용 완료] orderId: {}, discountPrice: {}", pendingOrder.getId(), discountPrice);
            }

            status = RollbackStatus.DECREASE_STOCK;
            decreaseStock(command.orderProducts(), pendingOrder.getId());
            log.debug("[재고 차감 완료] orderId: {}", pendingOrder.getId());

            // 3. [Final Stage] 결제 승인
            status = RollbackStatus.PAYED_ORDER;
            bfOrderWithdrawService.withdraw(pendingOrder, paymentPrice, command);
            log.debug("[Final Stage 완료] 주문 완료 - orderId: {}, status: ORDERED", pendingOrder.getId());

            return bfOrderService.updateStatusForComplete(pendingOrder.getId(), command);

        } catch (Exception e) {
            log.error("[주문 생성 실패] orderId: {}, error: {}", pendingOrder.getId(), e.getMessage(), e);
            bfOrderService.updateStatusForRollback(pendingOrder.getId(), status);
            throw e;
        }
    }

    private void validateAndLoadContext(CreateOrderCommand command) {
        bfOrderValidService.validateCompany(command.organizationId());
        bfOrderValidService.validateDuplicateOrder(command.idempotencyKey());
        if (command.couponId() != null) {
            bfOrderValidService.validateCoupon(command.userId(), command.couponId());
        }
    }

    private List<ProductResult> findAllOrderProduct(List<OrderProductReq> orderProducts) {
        List<ProductResult> allProducts = orderProductClient
                .findAllProducts(orderProducts.stream().map(OrderProductReq::productId).toList())
                .data();

        if (allProducts.size() != orderProducts.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return allProducts;
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
}
