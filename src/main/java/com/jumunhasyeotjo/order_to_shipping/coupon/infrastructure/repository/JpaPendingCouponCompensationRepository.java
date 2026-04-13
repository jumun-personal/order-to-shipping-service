package com.jumunhasyeotjo.order_to_shipping.coupon.infrastructure.repository;

import com.jumunhasyeotjo.order_to_shipping.coupon.domain.entity.PendingCouponCompensation;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.vo.CouponCompensationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaPendingCouponCompensationRepository extends JpaRepository<PendingCouponCompensation, UUID> {
    Optional<PendingCouponCompensation> findByOrderId(UUID orderId);
    List<PendingCouponCompensation> findAllByStatus(CouponCompensationStatus status, Pageable pageable);
}
