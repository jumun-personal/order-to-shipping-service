package com.jumunhasyeotjo.order_to_shipping.order.infrastructure.external;

import com.jumunhasyeotjo.order_to_shipping.coupon.application.CouponService;
import com.jumunhasyeotjo.order_to_shipping.coupon.application.IssueCouponService;
import com.jumunhasyeotjo.order_to_shipping.coupon.application.command.UseCouponCommand;
import com.jumunhasyeotjo.order_to_shipping.coupon.presentation.dto.res.CouponRes;
import com.jumunhasyeotjo.order_to_shipping.coupon.presentation.dto.res.IssueCouponRes;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderCouponClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderCouponInternalClientImpl implements OrderCouponClient {

    private final IssueCouponService issueCouponService;
    private final CouponService couponService;

    @Override
    public Integer useCoupon(UUID couponId, UUID orderId) {
        return issueCouponService.useCoupon(new UseCouponCommand(couponId, orderId));
    }

    @Override
    public CouponRes findCoupon(UUID couponId) {
        return CouponRes.fromResult(couponService.getCoupon(couponId));
    }

    @Override
    public boolean findIssuedCoupon(Long userId, UUID couponId) {
        return issueCouponService.existsByCouponIdAndUserId(couponId, userId);
    }
}
