package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.jumunhasyeotjo.order_to_shipping.common.exception.BusinessException;
import com.jumunhasyeotjo.order_to_shipping.common.exception.ErrorCode;
import com.jumunhasyeotjo.order_to_shipping.coupon.presentation.dto.res.IssueCouponRes;
import com.jumunhasyeotjo.order_to_shipping.order.application.OrderService;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderCompanyClient;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderCouponClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BFOrderValidService {

    private final OrderService orderService;
    private final OrderCompanyClient orderCompanyClient;
    private final OrderCouponClient orderCouponClient;

    public void validateCoupon(Long userId, UUID couponId) {
        boolean isValid = orderCouponClient.findIssuedCoupon(userId, couponId);
        if (!isValid) {
            log.warn("[검증 실패] 쿠폰 소유자 확인 실패 - couponId : {}, userId: {}", couponId, userId);
            throw new BusinessException(ErrorCode.INVALID_COUPON_OWNER);
        }
    }

    public void validateDuplicateOrder(String idempotencyKey) {
        if (orderService.existsByIdempotencyKey(idempotencyKey)) {
            log.warn("[검증 실패] 중복 주문 요청 - idempotencyKey: {}", idempotencyKey);
            throw new BusinessException(ErrorCode.DUPLICATE_ORDER);
        }
    }

    public void validateCompany(UUID companyId) {
        if (!orderCompanyClient.existCompany(companyId).data()) {
            log.error("[검증 실패] 존재하지 않는 업체 - companyId: {}", companyId);
            throw new BusinessException(ErrorCode.COMPANY_NOT_FOUND);
        }
    }
}