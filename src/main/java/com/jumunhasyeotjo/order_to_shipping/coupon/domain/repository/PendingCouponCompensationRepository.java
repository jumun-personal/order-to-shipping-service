package com.jumunhasyeotjo.order_to_shipping.coupon.domain.repository;

import com.jumunhasyeotjo.order_to_shipping.coupon.domain.entity.PendingCouponCompensation;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.vo.CouponCompensationStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingCouponCompensationRepository {
    PendingCouponCompensation save(PendingCouponCompensation compensation);
    Optional<PendingCouponCompensation> findByOrderId(UUID orderId);
    List<PendingCouponCompensation> findAllByStatus(CouponCompensationStatus status, Pageable pageable);
}
