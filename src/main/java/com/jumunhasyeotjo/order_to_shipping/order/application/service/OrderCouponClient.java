package com.jumunhasyeotjo.order_to_shipping.order.application.service;

import com.jumunhasyeotjo.order_to_shipping.common.aspect.Metric;
import com.jumunhasyeotjo.order_to_shipping.coupon.presentation.dto.res.CouponRes;
import com.jumunhasyeotjo.order_to_shipping.coupon.presentation.dto.res.IssueCouponRes;

import java.util.UUID;

public interface OrderCouponClient {
    @Metric(value = "쿠폰 사용 (내부)", level = Metric.Level.DEBUG)
    Integer useCoupon(UUID couponId, UUID orderId);

    @Metric(value = "쿠폰 조회 (내부)", level = Metric.Level.DEBUG)
    CouponRes findCoupon(UUID couponId);

    @Metric(value = "발급 쿠폰 조회 (내부)", level = Metric.Level.DEBUG)
    boolean findIssuedCoupon(Long userId, UUID couponId);
}
