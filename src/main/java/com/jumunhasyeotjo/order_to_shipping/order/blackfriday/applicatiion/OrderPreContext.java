package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.jumunhasyeotjo.order_to_shipping.coupon.presentation.dto.res.CouponRes;
import com.jumunhasyeotjo.order_to_shipping.order.application.dto.ProductResult;

import java.util.List;

public record OrderPreContext(
        List<ProductResult> productResultList,
        CouponRes couponRes
) {
}