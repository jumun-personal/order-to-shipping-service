package com.jumunhasyeotjo.order_to_shipping.coupon.application;

import com.jumunhasyeotjo.order_to_shipping.coupon.application.command.UseCouponCommand;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.entity.Coupon;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.entity.IssueCoupon;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.repository.CouponRepository;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.repository.IssueCouponRepository;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.vo.IssueCouponStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IssueCouponServiceTest {

    @Mock
    private CouponRepository couponRepository;
    @Mock
    private IssueCouponRepository issueCouponRepository;
    @Mock
    private CouponCompensationService couponCompensationService;

    @InjectMocks
    private IssueCouponService issueCouponService;

    @Test
    @DisplayName("쿠폰 사용 성공 시 pending compensation 존재 여부를 함께 확인한다")
    void useCoupon_whenCouponUsed_checksPendingCompensation() {
        UUID issueCouponId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        IssueCoupon issueCoupon = createIssuedCoupon();
        given(issueCouponRepository.findById(issueCouponId)).willReturn(issueCoupon);

        Integer discountAmount = issueCouponService.useCoupon(new UseCouponCommand(issueCouponId, orderId));

        assertThat(discountAmount).isEqualTo(issueCoupon.getCoupon().getDiscountAmount());
        assertThat(issueCoupon.getStatus()).isEqualTo(IssueCouponStatus.USED);
        assertThat(issueCoupon.getOrderId()).isEqualTo(orderId);
        verify(couponCompensationService).handleCouponUsed(orderId, issueCoupon);
    }

    private IssueCoupon createIssuedCoupon() {
        Coupon coupon = Coupon.createCoupon(
                "테스트 쿠폰",
                1000,
                10,
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1)
        );
        return IssueCoupon.issue(coupon, 1L);
    }
}
