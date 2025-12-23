package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.jumunhasyeotjo.order_to_shipping.order.application.command.CreateOrderCommand;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BfReservationService {
    private final RedisOutboxPublisher redisOutboxPublisher;

    /**
     * [Lua Atomic] 재고/쿠폰 예약 + Redis Streams Outbox 발행
     *
     * Lua Script를 사용하여 원자적으로 처리:
     * 1. 멱등성 체크
     * 2. 재고 차감 예약 (bf:stock:{productId} 형태)
     * 3. 쿠폰 사용 예약
     * 4. Redis Streams Outbox에 이벤트 발행
     */
    public String decreaseStockAndUseCoupon(Order order, CreateOrderCommand command) {
        try {
            // 이벤트 페이로드 생성
            OutboxEventPayload eventPayload = OutboxEventPayload.builder()
                    .orderId(order.getId())
                    .idempotencyKey(command.idempotencyKey())
                    .userId(command.userId())
                    .organizationId(command.organizationId())
                    .receiverCompanyId(order.getReceiverCompanyId())
                    .requestMessage(command.requestMessage())
                    .couponId(command.couponId())
                    .orderProducts(command.orderProducts())
                    .totalPrice(order.getTotalPrice())
                    .tossPaymentKey(command.tossPaymentKey())
                    .tossOrderId(command.tossOrderId())
                    .timestamp(LocalDateTime.now())
                    .build();

            // 상품 정보 매핑 (Lua Script용)
            List<Map<String, Object>> productList = command.orderProducts().stream()
                    .map(p -> {
                        Map<String, Object> productMap = new HashMap<>();
                        productMap.put("productId", p.productId().toString());
                        productMap.put("quantity", p.quantity());
                        return productMap;
                    })
                    .collect(Collectors.toList());

            // Lua Script 실행
            String messageId = redisOutboxPublisher.publishWithLuaScript(
                    order.getId(),
                    command.idempotencyKey(),
                    productList,
                    command.couponId(),
                    eventPayload
            );

            return messageId;

        } catch (Exception e) {
            e.printStackTrace();
            log.error("[Lua Atomic 실패] orderId: {}, error: {}", order.getId(), e.getMessage(), e);
            throw e;
        }
    }
}
