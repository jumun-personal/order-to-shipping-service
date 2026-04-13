package com.jumunhasyeotjo.order_to_shipping.coupon.infrastructure.repository;


import com.jumunhasyeotjo.order_to_shipping.coupon.domain.entity.IssueCoupon;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.repository.IssueCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IssueCouponRepositoryAdapter implements IssueCouponRepository {

    private final JpaIssueCouponRepository jpaIssueCouponRepository;

    @Override
    public IssueCoupon save(IssueCoupon issueCoupon) {
        return jpaIssueCouponRepository.save(issueCoupon);
    }

    @Override
    public IssueCoupon findById(UUID issueCouponId) {
        return jpaIssueCouponRepository.findById(issueCouponId).orElseThrow(IllegalArgumentException::new);
    }

    @Override
    public IssueCoupon findByOrderId(UUID orderId) {
        return jpaIssueCouponRepository.findByOrderId(orderId).orElseThrow(IllegalArgumentException::new);
    }

    @Override
    public Optional<IssueCoupon> findOptionalByOrderId(UUID orderId) {
        return jpaIssueCouponRepository.findByOrderId(orderId);
    }

    @Override
    public boolean existsByCoupon_CouponIdAndUserId(UUID couponId, Long userId) {
        return jpaIssueCouponRepository.existsByCoupon_CouponIdAndUserId(couponId, userId);
    }

    @Override
    public List<IssueCoupon> findByUserId(Long userId) {
        return jpaIssueCouponRepository.findByUserId(userId);
    }
}
