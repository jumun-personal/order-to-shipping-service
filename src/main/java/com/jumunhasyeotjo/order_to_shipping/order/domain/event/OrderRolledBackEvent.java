package com.jumunhasyeotjo.order_to_shipping.order.domain.event;

import com.jumunhasyeotjo.order_to_shipping.common.event.DomainEvent;
import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.RollbackPlan;
import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.RollbackStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class OrderRolledBackEvent implements DomainEvent {

    private final UUID orderId;
    private final RollbackStatus status;
    private final boolean compensateCoupon;
    private final boolean compensateStock;
    private final boolean compensatePayment;
    private final LocalDateTime occurredAt;

    public OrderRolledBackEvent(UUID orderId, RollbackPlan rollbackPlan) {
        this.orderId = orderId;
        this.status = rollbackPlan.status();
        this.compensateCoupon = rollbackPlan.compensateCoupon();
        this.compensateStock = rollbackPlan.compensateStock();
        this.compensatePayment = rollbackPlan.compensatePayment();
        this.occurredAt = LocalDateTime.now();
    }

    public static OrderRolledBackEvent of(UUID orderId, RollbackPlan rollbackPlan) {
        return new OrderRolledBackEvent(orderId, rollbackPlan);
    }

    public static OrderRolledBackEvent of(UUID orderId, RollbackStatus status) {
        return new OrderRolledBackEvent(orderId, new RollbackPlan(status, false, false, false));
    }

    @Override
    public LocalDateTime getOccurredAt() {
        return this.occurredAt;
    }
}
