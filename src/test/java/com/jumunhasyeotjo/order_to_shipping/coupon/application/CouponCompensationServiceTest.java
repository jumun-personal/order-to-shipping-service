package com.jumunhasyeotjo.order_to_shipping.coupon.application;

import com.jumunhasyeotjo.order_to_shipping.coupon.domain.entity.Coupon;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.entity.IssueCoupon;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.entity.PendingCouponCompensation;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.repository.IssueCouponRepository;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.repository.PendingCouponCompensationRepository;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.vo.CouponCompensationStatus;
import com.jumunhasyeotjo.order_to_shipping.coupon.domain.vo.IssueCouponStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CouponCompensationServiceTest {

    @Mock
    private PendingCouponCompensationRepository pendingCouponCompensationRepository;
    @Mock
    private IssueCouponRepository issueCouponRepository;

    @InjectMocks
    private CouponCompensationService couponCompensationService;

    @Test
    @DisplayName("보상 요청 시 이미 사용된 쿠폰이 있으면 즉시 취소하고 완료 상태로 저장한다")
    void requestCompensation_whenCouponAlreadyUsed_completesImmediately() {
        UUID orderId = UUID.randomUUID();
        PendingCouponCompensation compensation = PendingCouponCompensation.pending(orderId);
        IssueCoupon issueCoupon = createUsedCoupon(orderId);

        given(pendingCouponCompensationRepository.findByOrderId(orderId)).willReturn(Optional.empty());
        given(pendingCouponCompensationRepository.save(any(PendingCouponCompensation.class))).willReturn(compensation);
        given(issueCouponRepository.findOptionalByOrderId(orderId)).willReturn(Optional.of(issueCoupon));

        couponCompensationService.requestCompensation(orderId);

        assertThat(compensation.getStatus()).isEqualTo(CouponCompensationStatus.COMPLETED);
        assertThat(issueCoupon.getStatus()).isEqualTo(IssueCouponStatus.ISSUED);
        assertThat(issueCoupon.getOrderId()).isNull();
    }

    @Test
    @DisplayName("보상 요청 시 아직 사용된 쿠폰이 없으면 pending 상태로 보류한다")
    void requestCompensation_whenCouponNotUsedYet_keepsPending() {
        UUID orderId = UUID.randomUUID();
        PendingCouponCompensation compensation = PendingCouponCompensation.pending(orderId);

        given(pendingCouponCompensationRepository.findByOrderId(orderId)).willReturn(Optional.empty());
        given(pendingCouponCompensationRepository.save(any(PendingCouponCompensation.class))).willReturn(compensation);
        given(issueCouponRepository.findOptionalByOrderId(orderId)).willReturn(Optional.empty());

        couponCompensationService.requestCompensation(orderId);

        assertThat(compensation.getStatus()).isEqualTo(CouponCompensationStatus.PENDING);
    }

    @Test
    @DisplayName("쿠폰 사용이 나중에 성공하면 pending compensation을 즉시 완료한다")
    void handleCouponUsed_whenPendingExists_completesCompensation() {
        UUID orderId = UUID.randomUUID();
        PendingCouponCompensation compensation = PendingCouponCompensation.pending(orderId);
        IssueCoupon issueCoupon = createUsedCoupon(orderId);

        given(pendingCouponCompensationRepository.findByOrderId(orderId)).willReturn(Optional.of(compensation));

        couponCompensationService.handleCouponUsed(orderId, issueCoupon);

        assertThat(compensation.getStatus()).isEqualTo(CouponCompensationStatus.COMPLETED);
        assertThat(issueCoupon.getStatus()).isEqualTo(IssueCouponStatus.ISSUED);
        assertThat(issueCoupon.getOrderId()).isNull();
    }

    @Test
    @DisplayName("pending compensation이 일정 시간 안에 쿠폰 사용을 찾지 못하면 no-op으로 종료한다")
    void retryPendingCompensation_whenTimedOut_skipsCompensation() {
        UUID orderId = UUID.randomUUID();
        PendingCouponCompensation compensation = PendingCouponCompensation.pending(orderId);
        ReflectionTestUtils.setField(compensation, "createdAt", LocalDateTime.now().minusSeconds(31));

        given(pendingCouponCompensationRepository.findByOrderId(orderId)).willReturn(Optional.of(compensation));
        given(issueCouponRepository.findOptionalByOrderId(orderId)).willReturn(Optional.empty());

        couponCompensationService.retryPendingCompensation(orderId);

        assertThat(compensation.getStatus()).isEqualTo(CouponCompensationStatus.SKIPPED);
    }

    private IssueCoupon createUsedCoupon(UUID orderId) {
        Coupon coupon = Coupon.createCoupon(
                "테스트 쿠폰",
                1000,
                10,
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1)
        );
        IssueCoupon issueCoupon = IssueCoupon.issue(coupon, 1L);
        issueCoupon.useCoupon(orderId);
        return issueCoupon;
    }
}
