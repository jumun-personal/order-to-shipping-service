package com.jumunhasyeotjo.order_to_shipping.coupon.domain.repository;

import com.jumunhasyeotjo.order_to_shipping.coupon.domain.entity.IssueCoupon;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueCouponRepository {
    IssueCoupon save(IssueCoupon issueCoupon);
    IssueCoupon findById(UUID issueCouponId);
    IssueCoupon findByOrderId(UUID orderId);
    Optional<IssueCoupon> findOptionalByOrderId(UUID orderId);
    boolean existsByCoupon_CouponIdAndUserId(UUID couponId, Long userId);
    List<IssueCoupon> findByUserId(Long userId);
}
