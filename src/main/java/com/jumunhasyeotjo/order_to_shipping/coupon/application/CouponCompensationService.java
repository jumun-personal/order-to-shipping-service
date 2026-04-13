package com.jumunhasyeotjo.order_to_shipping.coupon.application;

import com.jumunhasyeotjo.order_to_shipping.coupon.domain.entity.IssueCoupon;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.entity.PendingCouponCompensation;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.repository.IssueCouponRepository;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.repository.PendingCouponCompensationRepository;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.vo.CouponCompensationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CouponCompensationService {

    private static final Duration PENDING_TIMEOUT = Duration.ofSeconds(30);

    private final PendingCouponCompensationRepository pendingCouponCompensationRepository;
    private final IssueCouponRepository issueCouponRepository;

    @Transactional
    public void requestCompensation(UUID orderId) {
        PendingCouponCompensation compensation = findOrCreatePending(orderId);
        if (compensation.isTerminal()) {
            log.info("[coupon-compensation] already completed. orderId={}", orderId);
            return;
        }
        if (tryCompleteCompensation(compensation, issueCouponRepository.findOptionalByOrderId(orderId).orElse(null))) {
            return;
        }
        log.info("[coupon-compensation] deferred. orderId={}", orderId);
    }

    @Transactional
    public void handleCouponUsed(UUID orderId, IssueCoupon issueCoupon) {
        pendingCouponCompensationRepository.findByOrderId(orderId)
                .filter(PendingCouponCompensation::isPending)
                .ifPresent(compensation -> completeCompensation(compensation, issueCoupon));
    }

    @Transactional(readOnly = true)
    public List<UUID> findPendingOrderIds(int batchSize) {
        return pendingCouponCompensationRepository.findAllByStatus(
                        CouponCompensationStatus.PENDING,
                        PageRequest.of(0, batchSize, Sort.by("createdAt").ascending())
                ).stream()
                .map(PendingCouponCompensation::getOrderId)
                .toList();
    }

    @Transactional
    public void retryPendingCompensation(UUID orderId) {
        pendingCouponCompensationRepository.findByOrderId(orderId)
                .filter(PendingCouponCompensation::isPending)
                .ifPresent(this::processPendingCompensation);
    }

    private void processPendingCompensation(PendingCouponCompensation compensation) {
        if (tryCompleteCompensation(compensation, issueCouponRepository.findOptionalByOrderId(compensation.getOrderId()).orElse(null))) {
            return;
        }
        if (compensation.isExpired(PENDING_TIMEOUT, LocalDateTime.now())) {
            compensation.markSkipped();
            log.info("[coupon-compensation] skipped after timeout. orderId={}", compensation.getOrderId());
        }
    }

    private boolean tryCompleteCompensation(PendingCouponCompensation compensation, IssueCoupon issueCoupon) {
        if (issueCoupon == null || !compensation.isPending()) {
            return false;
        }
        completeCompensation(compensation, issueCoupon);
        return true;
    }

    private void completeCompensation(PendingCouponCompensation compensation, IssueCoupon issueCoupon) {
        issueCoupon.cancelCoupon(compensation.getOrderId());
        compensation.markCompleted();
        log.info("[coupon-compensation] completed. orderId={}", compensation.getOrderId());
    }

    private PendingCouponCompensation findOrCreatePending(UUID orderId) {
        return pendingCouponCompensationRepository.findByOrderId(orderId)
                .orElseGet(() -> createPendingCompensation(orderId));
    }

    private PendingCouponCompensation createPendingCompensation(UUID orderId) {
        try {
            return pendingCouponCompensationRepository.save(PendingCouponCompensation.pending(orderId));
        } catch (DataIntegrityViolationException e) {
            return pendingCouponCompensationRepository.findByOrderId(orderId)
                    .orElseThrow(() -> e);
        }
    }
}
