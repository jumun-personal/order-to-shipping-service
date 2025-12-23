package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.jumunhasyeotjo.order_to_shipping.order.application.command.OrderProductReq;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Redis Outbox에 발행할 이벤트 페이로드
 * - OrderCreatedEvent (도메인 이벤트)와는 별도
 * - Worker가 처리할 데이터 포함
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventPayload {

    // 주문 정보
    private UUID orderId;
    private String idempotencyKey;
    private LocalDateTime timestamp;

    // 사용자 정보
    private Long userId;
    private UUID organizationId;
    private UUID receiverCompanyId;
    private String requestMessage;

    // 상품 정보
    private List<OrderProductReq> orderProducts;
    private Integer totalPrice;

    // 쿠폰 정보 (선택)
    private UUID couponId;

    // 결제 정보
    private String tossPaymentKey;
    private String tossOrderId;
}
