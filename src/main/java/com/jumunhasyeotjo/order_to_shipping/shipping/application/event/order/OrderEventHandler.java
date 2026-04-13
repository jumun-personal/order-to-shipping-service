package com.jumunhasyeotjo.order_to_shipping.shipping.application.event.order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.RollbackStatus;
import com.jumunhasyeotjo.order_to_shipping.payment.application.PaymentService;
import com.jumunhasyeotjo.order_to_shipping.payment.application.command.CancelPaymentCommand;
import com.jumunhasyeotjo.order_to_shipping.payment.presentation.dto.request.CancelPaymentReq;
import com.jumunhasyeotjo.order_to_shipping.shipping.application.ShippingService;
import com.jumunhasyeotjo.order_to_shipping.shipping.application.command.CancelShippingCommand;
import com.jumunhasyeotjo.order_to_shipping.shipping.application.command.CreateShippingCommand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventHandler {
	private final ShippingService shippingService;
	private final PaymentService paymentService;

	public void orderCreated(OrderCreatedEvent event){
		event.vendingOrders().forEach(vendingOrder -> {
			CreateShippingCommand command = new CreateShippingCommand(
				vendingOrder.getVendingOrderId(),
				event.orderId(),
				event.orderCreatedTime(),
				vendingOrder.getProductInfo(),
				event.requestMessage(),
				vendingOrder.getSupplierCompanyId(),
				event.receiverCompanyId()
			);

			shippingService.createShipping(command);
		});
	}


	public void orderCanceled(OrderCanceledEvent event){
		shippingService.cancelShippingList(event.orderId());

		CancelPaymentCommand command = new CancelPaymentCommand(
			event.orderId(),
			"주문취소"
		);
		paymentService.cancelPayment(command);
	}


	public void orderRolledBack(OrderRolledBackEvent event){
		if(event.compensatePayment() || event.status().equals(RollbackStatus.PAYED_ORDER)){
			CancelPaymentCommand command = new CancelPaymentCommand(
				event.orderId(),
				"주문 처리중 오류 발생"
			);

			paymentService.cancelPayment(command);
		}
	}

}
