package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.jumunhasyeotjo.order_to_shipping.common.exception.BusinessException;
import com.jumunhasyeotjo.order_to_shipping.common.exception.ErrorCode;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.CreateOrderCommand;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderPaymentClient;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BfOrderWithdrawService {
    private final OrderPaymentClient orderPaymentClient;

    public void withdraw(Order order, int totalPrice, CreateOrderCommand command) {
        UUID orderId = order.getId();
        log.info("[Final Stage Start] orderId: {}", orderId);

        try {
            // 1. 결제 승인 (출금) 호출
            boolean paymentSuccess = orderPaymentClient.confirmOrder(
                    totalPrice,
                    command.tossPaymentKey(),
                    command.tossOrderId(),
                    orderId
            );

            if (!paymentSuccess) {
                throw new BusinessException(ErrorCode.PAYMENT_FAILED);
            }

            log.info("[결제 승인 완료] orderId: {}", orderId);
        } catch (Exception e) {
            log.error("[결제 승인 실패] orderId: {}, error: {}", orderId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FINAL_STAGE_FAILED);
        }
    }
}