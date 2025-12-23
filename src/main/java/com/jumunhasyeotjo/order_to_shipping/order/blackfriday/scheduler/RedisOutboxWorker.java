package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.scheduler;

import com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion.BFOrderOutboxService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Redis Outbox Worker
 *
 * 역할:
 * 1. Redis Streams에서 이벤트 읽기 (XREADGROUP)
 * 2. 이벤트 처리 (비즈니스 로직)
 * 3. 처리 완료 후 상태 변경 (idempotent Status: Ready)
 * 4. ACK 전송 (at-least-once 보장)
 */
@Slf4j
@Component
public class RedisOutboxWorker {

    private final RedisTemplate<String, String> redisTemplate;
    private final BFOrderOutboxService orderOutboxService;

    private static final String STREAM_KEY = "outbox:order-created";
    private static final String CONSUMER_GROUP = "order-worker-group";
    private static final String CONSUMER_NAME = "order-worker-1";

    public RedisOutboxWorker(
            @Qualifier("BfredisTemplate") RedisTemplate<String, String> redisTemplate,
            BFOrderOutboxService orderOutboxService) {
        this.redisTemplate = redisTemplate;
        this.orderOutboxService = orderOutboxService;
    }

    @PostConstruct
    public void init() {
        createStreamAndConsumerGroup();
    }

    /**
     * Stream과 Consumer Group 생성
     * Stream이 없으면 먼저 생성하고, Consumer Group 생성
     */
    private void createStreamAndConsumerGroup() {
        try {
            // 1. Stream이 존재하는지 확인
            boolean streamExists = Boolean.TRUE.equals(redisTemplate.hasKey(STREAM_KEY));

            if (!streamExists) {
                // Stream이 없으면 더미 메시지를 추가하여 Stream 생성
                Map<String, Object> dummyMessage = new HashMap<>();
                dummyMessage.put("init", "true");
                dummyMessage.put("timestamp", String.valueOf(System.currentTimeMillis()));

                redisTemplate.opsForStream().add(STREAM_KEY, dummyMessage);
                log.info("[Stream 생성] stream: {}", STREAM_KEY);
            }

            // 2. Consumer Group 생성
            try {
                // StreamInfo를 사용하여 그룹 존재 여부 확인
                redisTemplate.opsForStream().groups(STREAM_KEY).stream()
                        .filter(group -> CONSUMER_GROUP.equals(group.groupName()))
                        .findFirst()
                        .ifPresentOrElse(
                                group -> log.info("[Consumer Group 이미 존재] group: {}", CONSUMER_GROUP),
                                () -> createConsumerGroup()
                        );
            } catch (Exception e) {
                // 그룹이 없으면 생성
                createConsumerGroup();
            }

            log.info("[Redis Outbox Worker 초기화 완료] stream: {}, group: {}", STREAM_KEY, CONSUMER_GROUP);

        } catch (Exception e) {
            log.error("[Redis Outbox Worker 초기화 실패] error: {}", e.getMessage(), e);
            throw new RuntimeException("Redis Outbox Worker 초기화 실패", e);
        }
    }

    /**
     * Consumer Group 생성
     */
    private void createConsumerGroup() {
        try {
            // ReadOffset.from("0-0"): Stream의 처음부터 읽기
            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0-0"), CONSUMER_GROUP);
            log.info("[Consumer Group 생성 완료] stream: {}, group: {}", STREAM_KEY, CONSUMER_GROUP);
        } catch (Exception e) {
            // BUSYGROUP 에러는 이미 존재한다는 의미이므로 무시
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.info("[Consumer Group 이미 존재] stream: {}, group: {}", STREAM_KEY, CONSUMER_GROUP);
            } else {
                throw e;
            }
        }
    }

    /**
     * [Tx-2] Redis Streams에서 이벤트 읽기 및 처리
     *
     * 스케줄러 방식으로 주기적으로 실행 (100ms마다)
     * 또는 @StreamListener 사용 가능
     */
    @Scheduled(fixedDelay = 100)
    public void processOutboxEvents() {
        try {
            // XREADGROUP으로 이벤트 읽기
            List<MapRecord<String, Object, Object>> messages = readMessagesFromStream();

            if (messages == null || messages.isEmpty()) {
                return;
            }

            log.info("[Worker 시작] 처리할 메시지 수: {}", messages.size());

            // 각 메시지 처리
            for (MapRecord<String, Object, Object> message : messages) {
                processMessage(message);
            }

        } catch (Exception e) {
            log.error("[Worker 실행 오류] error: {}", e.getMessage(), e);
        }
    }

    /**
     * XREADGROUP으로 메시지 읽기
     */
    private List<MapRecord<String, Object, Object>> readMessagesFromStream() {
        try {
            // XREADGROUP 옵션 설정
            Consumer consumer = Consumer.from(CONSUMER_GROUP, CONSUMER_NAME);
            StreamReadOptions readOptions = StreamReadOptions.empty()
                    .count(10)  // 한 번에 최대 10개 읽기
                    .block(Duration.ofMillis(100));  // 100ms 동안 대기

            // XREADGROUP 실행
            List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                    .read(consumer, readOptions, StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));

            return messages;

        } catch (Exception e) {
            log.error("[XREADGROUP 실패] error: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 개별 메시지 처리
     */

    private void processMessage(MapRecord<String, Object, Object> message) {
        String messageId = message.getId().getValue();
        String orderId = null;

        try {
            // 메시지에서 데이터 추출
            String payloadJson = (String) message.getValue().get("payload");
            String idempotencyKey = (String) message.getValue().get("idempotencyKey");
            orderId = (String) message.getValue().get("orderId");

            log.info("[Tx-2 Start] Redis Streams 메시지 처리 시작 - messageId: {}, orderId: {}", messageId, orderId);

            // 처리 완료: idempotent 상태를  'ready'로 변경
            orderOutboxService.setPayload(UUID.fromString(orderId), payloadJson);
            log.info("[Tx-2] OutBox 생성 완료 - orderId: {}, status: ready", orderId);

            // ACK 전송 (at-least-once 보장)
            commit(messageId);
            log.info("[Tx-2 End] Redis Streams 메시지 처리 완료 - messageId: {}, orderId: {}", messageId, orderId);

        } catch (Exception e) {
            log.error("[Tx-2 실패] messageId: {}, orderId: {}, error: {}",
                    messageId, orderId, e.getMessage(), e);

            // 실패 상태로 변경
            if (orderId != null) {
                orderOutboxService.updateOutboxStatusToFailed(UUID.fromString(orderId), e.getMessage());
            }

            // 재처리를 위해 ACK 하지 않음 (또는 Dead Letter Queue로 이동)
            handleFailedMessage(messageId, orderId, e);
        }
    }

//    /**
//     * 비즈니스 로직 처리
//     *
//     * 실제로는 여기서 외부 서비스 호출 등의 작업 수행
//     * 현재는 단순히 로그만 출력
//     */
//    private void processBusinessLogic(OutboxEventPayload payload) {
//        log.info("[비즈니스 로직] orderId: {}, totalPrice: {}, products: {}",
//                payload.getOrderId(),
//                payload.getTotalPrice(),
//                payload.getOrderProducts().size());
//
//        // TODO: 실제 비즈니스 로직 구현
//        // 예: 재고 확정, 쿠폰 확정, 알림 발송 등
//
//        // 시뮬레이션: 처리 시간
//        try {
//            Thread.sleep(100);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//    }

    /**
     * ACK 전송 (commit)
     */
    private void commit(String messageId) {
        try {
            redisTemplate.opsForStream()
                    .acknowledge(STREAM_KEY, CONSUMER_GROUP, messageId);
            log.debug("[ACK 전송] messageId: {}", messageId);
        } catch (Exception e) {
            log.error("[ACK 전송 실패] messageId: {}, error: {}", messageId, e.getMessage(), e);
        }
    }

    /**
     * 실패 메시지 처리
     *
     * 옵션:
     * 1. 재시도 (일정 횟수까지)
     * 2. Dead Letter Queue로 이동
     * 3. 알림 발송
     */
    private void handleFailedMessage(String messageId, String orderId, Exception e) {
        try {
            // 재시도 횟수 확인
            String retryKey = "retry:count:" + messageId;
            Long retryCount = redisTemplate.opsForValue().increment(retryKey);
            redisTemplate.expire(retryKey, Duration.ofHours(24));

            if (retryCount != null && retryCount > 3) {
                // 최대 재시도 횟수 초과: Dead Letter Queue로 이동
                moveToDeadLetterQueue(messageId, orderId, e.getMessage());
                commit(messageId); // ACK 전송하여 재처리 방지
            } else {
                // 재시도를 위해 ACK 하지 않음
                log.warn("[재시도 대기] messageId: {}, retryCount: {}", messageId, retryCount);
            }

        } catch (Exception ex) {
            log.error("[실패 처리 오류] messageId: {}, error: {}", messageId, ex.getMessage(), ex);
        }
    }

    /**
     * Dead Letter Queue로 이동
     */
    private void moveToDeadLetterQueue(String messageId, String orderId, String error) {
        try {
            Map<String, Object> deadLetterData = new HashMap<>();
            deadLetterData.put("originalMessageId", messageId);
            deadLetterData.put("orderId", orderId != null ? orderId : "unknown");
            deadLetterData.put("error", error);
            deadLetterData.put("timestamp", String.valueOf(System.currentTimeMillis()));

            // Redis Streams에 추가
            redisTemplate.opsForStream().add(
                    "outbox:dead-letter-queue",
                    deadLetterData
            );

            log.error("[Dead Letter Queue 이동] messageId: {}, orderId: {}", messageId, orderId);

        } catch (Exception e) {
            log.error("[Dead Letter Queue 이동 실패] messageId: {}, error: {}",
                    messageId, e.getMessage(), e);
        }
    }

}