package com.jumunhasyeotjo.order_to_shipping.coupon.application;


import com.jumunhasyeotjo.order_to_shipping.coupon.application.command.CancelCouponCommand;
import com.jumunhasyeotjo.order_to_shipping.coupon.application.command.IssueCouponCommand;
import com.jumunhasyeotjo.order_to_shipping.coupon.application.command.UseCouponCommand;
import com.jumunhasyeotjo.order_to_shipping.coupon.application.result.IssueCouponResult;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.entity.Coupon;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.entity.IssueCoupon;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.repository.CouponRepository;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.repository.IssueCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueCouponService {

    private final CouponRepository couponRepository;
    private final IssueCouponRepository issueCouponRepository;
    private final CouponCompensationService couponCompensationService;

    @Transactional
    public IssueCouponResult issue(IssueCouponCommand command) {
        UUID couponId = command.couponId();
        Long userId = command.userId();

        Coupon coupon = couponRepository.findById(couponId);
        coupon.issueCoupon();
        IssueCoupon issueCoupon = IssueCoupon.issue(coupon, userId);
        return IssueCouponResult.fromIssueCoupon(issueCouponRepository.save(issueCoupon));
    }

    @Transactional(readOnly = true)
    public List<IssueCouponResult> findByUserId(Long userId) {
        List<IssueCoupon> issueCoupons = issueCouponRepository.findByUserId(userId);
        return issueCoupons.stream().map(IssueCouponResult::fromIssueCoupon).collect(Collectors.toList());
    }

    @Transactional
    public Integer useCoupon(UseCouponCommand command) {
        UUID issueCouponId = command.issueCouponId();
        UUID orderId = command.orderId();

        IssueCoupon issueCoupon = issueCouponRepository.findById(issueCouponId);
        issueCoupon.useCoupon(orderId);
        couponCompensationService.handleCouponUsed(orderId, issueCoupon);
        return issueCoupon.getCoupon().getDiscountAmount();
    }

    @Transactional
    public IssueCouponResult cancelCoupon(CancelCouponCommand command) {
        UUID orderId = command.orderId();

        IssueCoupon issueCoupon = issueCouponRepository.findByOrderId(orderId);
        issueCoupon.cancelCoupon(orderId);
        return IssueCouponResult.fromIssueCoupon(issueCoupon);
    }

    @Transactional(readOnly = true)
    public IssueCouponResult getIssueCoupon(UUID issueCouponId) {
        return IssueCouponResult.fromIssueCoupon(issueCouponRepository.findById(issueCouponId));
    }

    @Transactional(readOnly = true)
    public boolean existsByCouponIdAndUserId(UUID couponId, Long userId) {
        return issueCouponRepository.existsByCoupon_CouponIdAndUserId(couponId, userId);
    }
}
