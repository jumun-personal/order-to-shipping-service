package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;


import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_bf_outbox",
        indexes = {
                @Index(name = "idx_status", columnList = "status"),
                @Index(name = "idx_order_id", columnList = "orderId"),
                @Index(name = "idx_created_at", columnList = "createdAt"),
                @Index(name = "idx_status_retry", columnList = "status, retryCount")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Outbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private UUID orderId;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(columnDefinition = "JSONB")
    @Type(JsonBinaryType.class)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private Integer retryCount = 0;

    @Column(nullable = false)
    private Integer maxRetryCount = 3;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime processedAt;

    private LocalDateTime failedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Builder
    public Outbox(UUID orderId, String idempotencyKey, String eventType,
                  String payload, Integer maxRetryCount) {
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
        this.eventType = eventType;
        this.payload = payload;
        this.maxRetryCount = maxRetryCount != null ? maxRetryCount : 3;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 상태 변경 메서드
    public void markAsReady() {
        this.status = OutboxStatus.READY;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsProcessing() {
        this.status = OutboxStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsComplete() {
        this.status = OutboxStatus.COMPLETE;
        this.processedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsFailed(String errorMessage) {
        this.status = OutboxStatus.FAILED;
        this.errorMessage = errorMessage;
        this.failedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean canRetry() {
        return this.retryCount < this.maxRetryCount;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public enum OutboxStatus {
        PENDING,      // 초기 상태
        PROCESSING,   // 처리 중
        READY,        // Worker 처리 완료 (Final Stage 대기)
        COMPLETE,     // Final Stage 완료
        FAILED        // 실패
    }
}
