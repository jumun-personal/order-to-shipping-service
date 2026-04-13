package com.jumunhasyeotjo.order_to_shipping.coupon.infrastructure.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderRollbackEvent(
    UUID orderId,
    String status,
    boolean compensateCoupon,
    boolean compensateStock,
    boolean compensatePayment,
    LocalDateTime occurredAt
) {
}
