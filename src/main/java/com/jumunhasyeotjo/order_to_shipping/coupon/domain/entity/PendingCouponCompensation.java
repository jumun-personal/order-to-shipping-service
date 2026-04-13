package com.jumunhasyeotjo.order_to_shipping.coupon.domain.entity;

import com.jumunhasyeotjo.order_to_shipping.common.entity.BaseEntity;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.vo.CouponCompensationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "p_coupon_compensation",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_coupon_compensation_order", columnNames = "orderId")
    }
)
public class PendingCouponCompensation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID compensationId;

    @Column(nullable = false, updatable = false, unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponCompensationStatus status;

    private LocalDateTime processedAt;

    public static PendingCouponCompensation pending(UUID orderId) {
        PendingCouponCompensation compensation = new PendingCouponCompensation();
        compensation.orderId = orderId;
        compensation.status = CouponCompensationStatus.PENDING;
        return compensation;
    }

    public boolean isPending() {
        return status == CouponCompensationStatus.PENDING;
    }

    public boolean isTerminal() {
        return status == CouponCompensationStatus.COMPLETED || status == CouponCompensationStatus.SKIPPED;
    }

    public boolean isExpired(Duration timeout, LocalDateTime now) {
        LocalDateTime createdAt = getCreatedAt();
        return createdAt != null && !createdAt.plus(timeout).isAfter(now);
    }

    public void markCompleted() {
        this.status = CouponCompensationStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
    }

    public void markSkipped() {
        this.status = CouponCompensationStatus.SKIPPED;
        this.processedAt = LocalDateTime.now();
    }
}
