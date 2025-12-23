package com.jumunhasyeotjo.order_to_shipping.order.domain.event;

import com.jumunhasyeotjo.order_to_shipping.common.event.DomainEvent;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.OrderProductReq;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.OrderProduct;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.VendorOrder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bf주문 생성 이벤트
 *
 * Kafka로 발행되어 다른 서비스들이 소비
 * - 재고 서비스: products 정보로 재고 차감
 * - 쿠폰 서비스: couponId, userId 정보로 쿠폰 사용 처리
 * - 배송 서비스: 주문 정보로 배송 준비
 */
@Getter
public class BfOrderCreatedEvent implements DomainEvent {

    private final String eventType = "BF_ORDER_CREATED";

    // 주문 기본 정보
    private final UUID orderId;
    private final Long userId;
    private final UUID organizationId;
    private final UUID receiverCompanyId;
    private final String requestMessage;
    private final Integer totalPrice;

    // 상품 정보 (재고 차감용)
    private final List<OrderProductInfo> products;

    // 쿠폰 정보 (쿠폰 사용 처리용)
    private final CouponInfo coupon;

    // 결제 정보
    private final PaymentInfo payment;

    private final LocalDateTime occurredAt;

    private BfOrderCreatedEvent(UUID orderId, Long userId, UUID organizationId,
                                UUID receiverCompanyId, String requestMessage,
                                Integer totalPrice, List<OrderProductInfo> products,
                                CouponInfo coupon, PaymentInfo payment) {
        this.orderId = orderId;
        this.userId = userId;
        this.organizationId = organizationId;
        this.receiverCompanyId = receiverCompanyId;
        this.requestMessage = requestMessage;
        this.totalPrice = totalPrice;
        this.products = products;
        this.coupon = coupon;
        this.payment = payment;
        this.occurredAt = LocalDateTime.now();
    }

    public static OrderCreatedEvent of(Order order, List<VendorOrder> vendorOrders) {
        // 1. 엔티티(VendorOrder) 리스트 이벤트용 DTO(VendingOrder) 리스트로 변환
        List<OrderCreatedEvent.VendingOrder> vendingOrderDtos = vendorOrders.stream()
                .map(vo -> new OrderCreatedEvent.VendingOrder(
                        vo.getId(),
                        vo.getSupplierCompanyId(),
                        getProductInfo(vo.getOrderProducts()) // 각 공급사별 주문 상품 정보 생성
                ))
                .toList();

        // 2. 이벤트 객체 생성
        return new OrderCreatedEvent(
                order.getId(),
                order.getCreatedAt(),
                order.getRequestMessage(),
                order.getReceiverCompanyId(),
                vendingOrderDtos
        );
    }

    public static BfOrderCreatedEvent of(UUID orderId, Long userId, UUID organizationId,
                                         UUID receiverCompanyId, String requestMessage,
                                         Integer totalPrice, List<OrderProductReq> orderProducts,
                                         UUID couponId, String tossPaymentKey, String tossOrderId) {
        List<OrderProductInfo> products = orderProducts.stream()
                .map(p -> new OrderProductInfo(p.productId(), p.quantity()))
                .toList();

        CouponInfo coupon = couponId != null
                ? new CouponInfo(couponId, userId)
                : null;

        PaymentInfo payment = new PaymentInfo(tossPaymentKey, tossOrderId);

        return new BfOrderCreatedEvent(
                orderId,
                userId,
                organizationId,
                receiverCompanyId,
                requestMessage,
                totalPrice,
                products,
                coupon,
                payment
        );
    }


    @Override
    public LocalDateTime getOccurredAt() {
        return this.occurredAt;
    }

    /**
     * 주문 상품 정보 (재고 차감용)
     */
    @Getter
    public static class OrderProductInfo {
        private final UUID productId;
        private final Integer quantity;

        public OrderProductInfo(UUID productId, Integer quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }
    }

    /**
     * 쿠폰 정보 (쿠폰 사용 처리용)
     */
    @Getter
    public static class CouponInfo {
        private final UUID couponId;
        private final Long userId;

        public CouponInfo(UUID couponId, Long userId) {
            this.couponId = couponId;
            this.userId = userId;
        }
    }

    /**
     * 결제 정보
     */
    @Getter
    public static class PaymentInfo {
        private final String tossPaymentKey;
        private final String tossOrderId;

        public PaymentInfo(String tossPaymentKey, String tossOrderId) {
            this.tossPaymentKey = tossPaymentKey;
            this.tossOrderId = tossOrderId;
        }
    }

    private static String getProductInfo(List<OrderProduct> orderProducts) {
        return orderProducts.stream()
                .map(p -> String.format("%s %d박스", p.getName(), p.getQuantity()))
                .collect(Collectors.joining(", "));
    }
}