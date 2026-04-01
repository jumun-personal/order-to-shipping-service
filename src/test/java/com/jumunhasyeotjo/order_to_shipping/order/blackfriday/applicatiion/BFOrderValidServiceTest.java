package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.jumunhasyeotjo.order_to_shipping.common.exception.BusinessException;
import com.jumunhasyeotjo.order_to_shipping.common.exception.ErrorCode;
import com.jumunhasyeotjo.order_to_shipping.order.application.OrderService;
import com.jumunhasyeotjo.order_to_shipping.order.application.dto.ExternalExists;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderCompanyClient;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderCouponClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BFOrderValidServiceTest {

    @Mock
    private OrderService orderService;
    @Mock
    private OrderCompanyClient orderCompanyClient;
    @Mock
    private OrderCouponClient orderCouponClient;

    @InjectMocks
    private BFOrderValidService bfOrderValidService;

    @Test
    @DisplayName("중복 idempotencyKey는 주문 생성에 실패한다")
    void validateDuplicateOrder_whenDuplicate_throwsException() {
        given(orderService.existsByIdempotencyKey("dup-key")).willReturn(true);

        assertThatThrownBy(() -> bfOrderValidService.validateDuplicateOrder("dup-key"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_ORDER);
    }

    @Test
    @DisplayName("존재하지 않는 업체는 주문 생성에 실패한다")
    void validateCompany_whenCompanyNotExists_throwsException() {
        UUID companyId = UUID.randomUUID();
        given(orderCompanyClient.existCompany(companyId)).willReturn(new ExternalExists(false));

        assertThatThrownBy(() -> bfOrderValidService.validateCompany(companyId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("쿠폰 소유자가 다르면 주문 생성에 실패한다")
    void validateCoupon_whenCouponOwnerInvalid_throwsException() {
        UUID couponId = UUID.randomUUID();
        given(orderCouponClient.findIssuedCoupon(1L, couponId)).willReturn(false);

        assertThatThrownBy(() -> bfOrderValidService.validateCoupon(1L, couponId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_COUPON_OWNER);
    }
}
