package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion.BFOrderOutboxService;
import com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion.Outbox;
import com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion.OutboxEventPayload;
import com.jumunhasyeotjo.order_to_shipping.order.domain.event.BfOrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DB Outbox 폴링 → Kafka 이벤트 발행
 *
 * 역할:
 * 1. DB Outbox 테이블에서 READY 상태이면서 payload가 있는 레코드 조회
 * 2. BfOrderCreatedEvent 단일 이벤트 발행
 *    - 재고 서비스가 products 정보로 재고 차감
 *    - 쿠폰 서비스가 coupon 정보로 쿠폰 사용 처리
 * 3. Outbox 상태를 COMPLETE로 변경
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BfOrderOutboxRelay {

    private final BFOrderOutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.topic.order-events:order}")
    private String BF_ORDER_TOPIC;

    private static final int BATCH_SIZE = 10;
    private int currentPage = 0;

    /**
     * Outbox 폴링 및 Kafka 발행 (페이지네이션)
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relayOutboxToKafka() {
        try {
            List<Outbox> readyOutboxes = outboxService.findReadyOutboxes(currentPage, BATCH_SIZE);

            if (readyOutboxes.isEmpty()) {
                if (currentPage > 0) {
                    log.debug("[Outbox Relay] 마지막 페이지 도달, 페이지 리셋");
                    currentPage = 0;
                }
                return;
            }

            log.info("[Outbox Relay 시작] page: {}, 처리 대상: {}건", currentPage, readyOutboxes.size());

            int successCount = 0;
            int failCount = 0;

            for (Outbox outbox : readyOutboxes) {
                try {
                    if (outbox.getPayload() == null) {
                        log.warn("[Outbox Relay 스킵] outboxId: {}, orderId: {} - payload 없음",
                                outbox.getId(), outbox.getOrderId());
                        continue;
                    }

                    processOutbox(outbox);
                    successCount++;

                } catch (Exception e) {
                    log.error("[Outbox Relay 실패] outboxId: {}, orderId: {}, error: {}",
                            outbox.getId(), outbox.getOrderId(), e.getMessage(), e);

                    failCount++;

                    try {
                        outboxService.incrementRetryCount(outbox.getOrderId());
                    } catch (Exception ex) {
                        log.error("[재시도 횟수 증가 실패] orderId: {}", outbox.getOrderId(), ex);
                    }
                }
            }

            log.info("[Outbox Relay 완료] page: {}, 성공: {}건, 실패: {}건",
                    currentPage, successCount, failCount);

            currentPage++;

        } catch (Exception e) {
            log.error("[Outbox Relay 오류] page: {}, error: {}", currentPage, e.getMessage(), e);
            currentPage = 0;
        }
    }

    /**
     * 개별 Outbox 처리 (트랜잭션)
     *
     * 원자적으로 처리:
     * 1. BfOrderCreatedEvent 발행
     * 2. Outbox 상태 COMPLETE로 변경
     */
    public void processOutbox(Outbox outbox) {
        try {
            log.info("[Outbox 처리 시작] outboxId: {}, orderId: {}", outbox.getId(), outbox.getOrderId());

            // Payload 파싱
            OutboxEventPayload payload = parsePayload(outbox.getPayload());

            // BfOrderCreatedEvent 생성 및 발행
            BfOrderCreatedEvent event = BfOrderCreatedEvent.of(
                    payload.getOrderId(),
                    payload.getUserId(),
                    payload.getOrganizationId(),
                    payload.getReceiverCompanyId(),
                    payload.getRequestMessage(),
                    payload.getTotalPrice(),
                    payload.getOrderProducts(),
                    payload.getCouponId(),
                    payload.getTossPaymentKey(),
                    payload.getTossOrderId()
            );

            publishBfOrderCreatedEvent(event);

            // Outbox 상태 COMPLETE로 변경
            outboxService.updateOutboxStatusToComplete(outbox.getOrderId());

            log.info("[Outbox 처리 완료] outboxId: {}, orderId: {}", outbox.getId(), outbox.getOrderId());

        } catch (Exception e) {
            log.error("[Outbox 처리 실패] outboxId: {}, orderId: {}, error: {}",
                    outbox.getId(), outbox.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * BfOrderCreatedEvent를 Kafka로 발행
     *
     * 단일 이벤트에 모든 정보 포함:
     * - products: 재고 서비스가 재고 차감
     * - coupon: 쿠폰 서비스가 쿠폰 사용 처리
     * - payment: 결제 정보
     */
    private void publishBfOrderCreatedEvent(BfOrderCreatedEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);

            ProducerRecord<String, String> record = new ProducerRecord<>(BF_ORDER_TOPIC, eventJson);
            record.headers().add("eventType",event.getEventType().getBytes());

            SendResult<String, String> result = kafkaTemplate.send(record).get();

            log.info("[BfOrderCreatedEvent 발행 완료] orderId: {}, partition: {}, offset: {}, " +
                            "products: {}개, coupon: {}",
                    event.getOrderId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    event.getProducts().size(),
                    event.getCoupon() != null ? event.getCoupon().getCouponId() : "없음");

        } catch (Exception e) {
            log.error("[BfOrderCreatedEvent 발행 실패] orderId: {}, error: {}",
                    event.getOrderId(), e.getMessage(), e);
            throw new RuntimeException("BfOrderCreatedEvent 발행 실패", e);
        }
    }

    private OutboxEventPayload parsePayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, OutboxEventPayload.class);
        } catch (Exception e) {
            log.error("[Payload 파싱 실패] error: {}", e.getMessage(), e);
            throw new RuntimeException("Payload 파싱 실패", e);
        }
    }
}