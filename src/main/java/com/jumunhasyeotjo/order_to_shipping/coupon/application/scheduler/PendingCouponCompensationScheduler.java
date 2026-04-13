package com.jumunhasyeotjo.order_to_shipping.coupon.application.scheduler;

import com.jumunhasyeotjo.order_to_shipping.coupon.application.CouponCompensationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class PendingCouponCompensationScheduler {

    private static final int BATCH_SIZE = 20;

    private final CouponCompensationService couponCompensationService;

    @Scheduled(fixedDelay = 3000)
    public void retryPendingCompensations() {
        for (UUID orderId : couponCompensationService.findPendingOrderIds(BATCH_SIZE)) {
            try {
                couponCompensationService.retryPendingCompensation(orderId);
            } catch (Exception e) {
                log.error("[coupon-compensation] retry failed. orderId={}", orderId, e);
            }
        }
    }
}
