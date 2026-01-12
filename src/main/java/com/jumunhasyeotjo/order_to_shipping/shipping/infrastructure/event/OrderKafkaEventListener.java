package com.jumunhasyeotjo.order_to_shipping.shipping.infrastructure.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeotjo.order_to_shipping.common.inbox.InboxService;
import com.jumunhasyeotjo.order_to_shipping.common.util.JsonUtil;
import com.jumunhasyeotjo.order_to_shipping.common.util.KafkaUtil;
import com.jumunhasyeotjo.order_to_shipping.shipping.application.event.order.OrderCanceledEvent;
import com.jumunhasyeotjo.order_to_shipping.shipping.application.event.order.OrderCreatedEvent;
import com.jumunhasyeotjo.order_to_shipping.shipping.application.event.order.OrderEventHandler;
import com.jumunhasyeotjo.order_to_shipping.shipping.application.event.order.OrderRolledBackEvent;
import com.jumunhasyeotjo.order_to_shipping.shipping.infrastructure.event.type.OrderEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderKafkaEventListener {

	private final OrderEventHandler orderEventHandler;
	private final ObjectMapper objectMapper;
	private final InboxService inboxService;
	private final JsonUtil jsonUtil;

	@KafkaListener(
		topics = "${spring.kafka.topics.order}",
		groupId = "${spring.kafka.consumer.group-id}",
		containerFactory = "kafkaListenerContainerFactory"
	)
	public void listen(
		@Payload String event,
		@Header(name = "eventType", required = false) String eventType
	) {
		try {
			dispatch(event, eventType);
		}catch (Exception e){
			log.error("Error processing event: {}", e.getMessage(), e);
		}
	}

	public void dispatch(String payload, String eventType) throws JsonProcessingException {
		switch (OrderEvent.ofString(eventType)){
			case CREATED ->{
				OrderCreatedEvent orderCreatedEvent = objectMapper.readValue(payload, OrderCreatedEvent.class);
				inboxService.process(orderCreatedEvent.orderId().toString(), OrderEvent.CREATED.getEventName(), jsonUtil.toJson(orderCreatedEvent),
				() -> orderEventHandler.orderCreated(orderCreatedEvent));
			}
			case CANCELED -> {
				OrderCanceledEvent orderCanceledEvent = objectMapper.readValue(payload, OrderCanceledEvent.class);
				inboxService.process(orderCanceledEvent.orderId().toString(), OrderEvent.CANCELED.getEventName(), jsonUtil.toJson(orderCanceledEvent),
					() -> orderEventHandler.orderCanceled(orderCanceledEvent));
			}
			case ROLLED_BACK -> {
				OrderRolledBackEvent orderRolledBackEvent = objectMapper.readValue(payload, OrderRolledBackEvent.class);
				inboxService.process(orderRolledBackEvent.orderId().toString(), OrderEvent.ROLLED_BACK.getEventName(), jsonUtil.toJson(orderRolledBackEvent),
					() -> orderEventHandler.orderRolledBack(orderRolledBackEvent));
			}
			default -> log.info("UNHANDLED {}",eventType);
		}
	}
}
