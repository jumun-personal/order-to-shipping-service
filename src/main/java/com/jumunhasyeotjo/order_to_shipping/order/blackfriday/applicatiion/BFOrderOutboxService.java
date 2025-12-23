package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeotjo.order_to_shipping.common.exception.BusinessException;
import com.jumunhasyeotjo.order_to_shipping.common.exception.ErrorCode;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BFOrderOutboxService {

    private final BfOutboxRepository outboxRepository;
    private final BFOrderService bfOrderService;
    private final ObjectMapper objectMapper;

    /**
     * [Final Stage] 결제 승인 + Outbox/SnapShot 상태 갱신
     *
     * - 결제 승인 (출금) 호출
     * - Outbox 상태를 'complete'로 갱신
     * - Order 상태를 'ORDERED'로 갱신
     * - 두 상태 갱신은 하나의 트랜잭션으로 묶음
     */
    @Transactional
    public Order executeStatusUpdate(Order order) {
        UUID orderId = order.getId();
        log.info("[Final Stage Start] orderId: {}", orderId);

        try {
            // → 하나의 트랜잭션으로 묶음
            markAsReady(orderId);
            Order completedOrder = bfOrderService.updateStatusForComplete(
                    orderId,
                    order.getVendorOrders()
            );

            log.info("[Final Stage End] Outbox/SnapShot 상태 갱신 완료 - orderId: {}", orderId);
            return completedOrder;

        } catch (Exception e) {
            e.printStackTrace();
            log.error("[Final Stage 실패] orderId: {}, error: {}", orderId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FINAL_STAGE_FAILED);
        }
    }

    /**
     * Outbox 생성
     *
     * Lua Script 실행 후 DB에도 기록
     * (Redis 장애 대비 이중화)
     */
    @Transactional
    public Outbox createOutbox(UUID orderId, String idempotencyKey,
                               String eventType, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);

            Outbox outbox = Outbox.builder()
                    .orderId(orderId)
                    .idempotencyKey(idempotencyKey)
                    .eventType(eventType)
                    .payload(payloadJson)
                    .maxRetryCount(3)
                    .build();

            Outbox saved = outboxRepository.save(outbox);
            log.info("[Outbox 생성] id: {}, orderId: {}", saved.getId(), orderId);

            return saved;

        } catch (Exception e) {
            log.error("[Outbox 생성 실패] orderId: {}, error: {}", orderId, e.getMessage(), e);
            e.printStackTrace();
            throw new BusinessException(ErrorCode.OUTBOX_PUBLISH_FAILED);
        }
    }

    /**
     * Outbox 상태를 'COMPLETE'로 변경
     *
     * Final Stage에서 결제 승인 및 주문 확정 후 호출
     */
    @Transactional
    public void updateOutboxStatusToComplete(UUID orderId) {
        try {
            Outbox outbox = outboxRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.OUTBOX_NOT_FOUND));

            outbox.markAsComplete();
            outboxRepository.save(outbox);

            log.info("[상태 변경: COMPLETE] outboxId: {}, orderId: {}", outbox.getId(), orderId);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[상태 변경 실패: COMPLETE] orderId: {}, error: {}", orderId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FINAL_STAGE_FAILED);
        }
    }

    /**
     * Outbox 상태를 'FAILED'로 변경
     *
     * 처리 중 오류 발생 시 호출
     */
    @Transactional
    public void updateOutboxStatusToFailed(UUID orderId, String errorMessage) {
        try {
            Outbox outbox = outboxRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.OUTBOX_NOT_FOUND));

            outbox.markAsFailed(errorMessage);
            outboxRepository.save(outbox);

            log.error("[상태 변경: FAILED] outboxId: {}, orderId: {}, error: {}",
                    outbox.getId(), orderId, errorMessage);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[상태 변경 실패: FAILED] orderId: {}, error: {}", orderId, e.getMessage(), e);
            // 실패 처리 중 오류는 throw 하지 않음
        }
    }


    /**
     * N번 이상 결과 나오지 않은 데이터 조회
     *
     * 스케줄러에서 주기적으로 호출하여 재처리
     *
     * @param minutesAgo 몇 분 이전 데이터부터 조회할지
     * @return 재처리 대상 Outbox 목록
     */
    @Transactional(readOnly = true)
    public List<Outbox> findPendingOutboxesForRetry(int minutesAgo) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutesAgo);
        List<Outbox> outboxes = outboxRepository.findPendingOutboxesForRetry(threshold);

        log.info("[재처리 대상 조회] 개수: {}, threshold: {}", outboxes.size(), threshold);
        return outboxes;
    }

    /**
     * 실패한 Outbox 조회 (최대 재시도 횟수 초과)
     */
    @Transactional(readOnly = true)
    public List<Outbox> findFailedOutboxes() {
        List<Outbox> outboxes = outboxRepository.findFailedOutboxes();
        log.info("[실패 데이터 조회] 개수: {}", outboxes.size());
        return outboxes;
    }

    /**
     * 완료된 Outbox 정리 (24시간 이상 지난 데이터)
     */
    @Transactional
    public void cleanupCompletedOutboxes() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        List<Outbox> completedOutboxes = outboxRepository
                .findByStatusAndUpdatedAtBefore(Outbox.OutboxStatus.COMPLETE, threshold);

        if (!completedOutboxes.isEmpty()) {
            outboxRepository.deleteAll(completedOutboxes);
            log.info("[Outbox 정리] 삭제된 개수: {}", completedOutboxes.size());
        }
    }



    @Transactional
    public void markAsReady(UUID orderId) {
        Outbox outbox = outboxRepository.findByOrderId(orderId).orElseThrow(() -> new BusinessException(ErrorCode.OUTBOX_NOT_INIT));
        outbox.markAsReady();
    }

    @Transactional
    public void setPayload(UUID orderId, String payload) {
        Outbox outbox = outboxRepository.findByOrderId(orderId).orElseThrow(() -> new BusinessException(ErrorCode.OUTBOX_NOT_INIT));
        outbox.setPayload(payload);
    }

    /**
     * READY 상태이면서 payload가 있는 Outbox 조회 (Kafka 발행 대상)
     *
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기
     * @return READY 상태의 Outbox 목록
     */
    @Transactional(readOnly = true)
    public List<Outbox> findReadyOutboxes(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        List<Outbox> outboxes = outboxRepository.findReadyOutboxesWithPayload(pageable);

        log.debug("[READY Outbox 조회] page: {}, size: {}, 조회된 개수: {}",
                page, size, outboxes.size());

        return outboxes;
    }


    /**
     * 재시도 횟수 증가
     */
    @Transactional
    public void incrementRetryCount(UUID orderId) {
        Outbox outbox = outboxRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OUTBOX_NOT_FOUND));

        outbox.incrementRetryCount();
        outboxRepository.save(outbox);

        log.info("[재시도 횟수 증가] outboxId: {}, orderId: {}, retryCount: {}",
                outbox.getId(), orderId, outbox.getRetryCount());
    }

}
