package com.jumunhasyeotjo.order_to_shipping.order.domain.event;

import com.jumunhasyeotjo.order_to_shipping.common.event.DomainEvent;
import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.RollbackStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bf주문 생성 이벤트
 *
 * Kafka로 발행되어 다른 서비스들이 소비
 * - 재고 서비스: products 정보로 재고 차감
 * - 쿠폰 서비스: couponId, userId 정보로 쿠폰 사용 처리
 * - 배송 서비스: 주문 정보로 배송 준비
 */
@Getter
public class BfOrderRolledBackEvent implements DomainEvent {

    private final UUID orderId;
    private final RollbackStatus status;
    private final LocalDateTime occurredAt;

    public BfOrderRolledBackEvent(UUID orderId, RollbackStatus status) {
        this.orderId = orderId;
        this.status = status;
        this.occurredAt = LocalDateTime.now();
    }

    public static OrderRolledBackEvent of(UUID orderId, RollbackStatus status) {
        return new OrderRolledBackEvent(
                orderId,
                status
        );
    }

    @Override
    public LocalDateTime getOccurredAt() {
        return this.occurredAt;
    }
}