package com.jumunhasyeotjo.order_to_shipping.shipping.application.event.order;

import java.time.LocalDateTime;
import java.util.UUID;

import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.RollbackStatus;

public record OrderRolledBackEvent(
	UUID orderId,
	RollbackStatus status, //todo 타입변경
	boolean compensatePayment,
	LocalDateTime occurredAt
) {
}
