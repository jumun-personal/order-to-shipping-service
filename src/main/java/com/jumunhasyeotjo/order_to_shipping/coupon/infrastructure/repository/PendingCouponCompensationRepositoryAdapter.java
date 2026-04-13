package com.jumunhasyeotjo.order_to_shipping.coupon.infrastructure.repository;

import com.jumunhasyeotjo.order_to_shipping.coupon.domain.entity.PendingCouponCompensation;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.repository.PendingCouponCompensationRepository;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.vo.CouponCompensationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PendingCouponCompensationRepositoryAdapter implements PendingCouponCompensationRepository {

    private final JpaPendingCouponCompensationRepository jpaPendingCouponCompensationRepository;

    @Override
    public PendingCouponCompensation save(PendingCouponCompensation compensation) {
        return jpaPendingCouponCompensationRepository.save(compensation);
    }

    @Override
    public Optional<PendingCouponCompensation> findByOrderId(UUID orderId) {
        return jpaPendingCouponCompensationRepository.findByOrderId(orderId);
    }

    @Override
    public List<PendingCouponCompensation> findAllByStatus(CouponCompensationStatus status, Pageable pageable) {
        return jpaPendingCouponCompensationRepository.findAllByStatus(status, pageable);
    }
}
