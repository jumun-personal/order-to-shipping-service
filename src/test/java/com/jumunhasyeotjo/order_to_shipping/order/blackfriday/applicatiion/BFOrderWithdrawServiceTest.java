package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.jumunhasyeotjo.order_to_shipping.common.exception.BusinessException;
import com.jumunhasyeotjo.order_to_shipping.common.exception.ErrorCode;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.CreateOrderCommand;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.OrderProductReq;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderPaymentClient;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static com.jumunhasyeotjo.order_to_shipping.order.fixtures.OrderFixtures.getOrder;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BFOrderWithdrawServiceTest {

    @Mock
    private OrderPaymentClient orderPaymentClient;

    @InjectMocks
    private BFOrderWithdrawService bfOrderWithdrawService;

    @Test
    @DisplayName("결제 승인 성공")
    void withdraw_success() {
        Order order = createOrder();
        CreateOrderCommand command = createCommand();
        given(orderPaymentClient.confirmOrder(1000, command.tossPaymentKey(), command.tossOrderId(), order.getId()))
                .willReturn(true);

        assertThatCode(() -> bfOrderWithdrawService.withdraw(order, 1000, command))
                .doesNotThrowAnyException();

        verify(orderPaymentClient).confirmOrder(1000, command.tossPaymentKey(), command.tossOrderId(), order.getId());
    }

    @Test
    @DisplayName("결제 승인 실패(false) 시 FINAL_STAGE_FAILED 예외")
    void withdraw_whenPaymentReturnsFalse_throwsFinalStageFailed() {
        Order order = createOrder();
        CreateOrderCommand command = createCommand();
        given(orderPaymentClient.confirmOrder(1000, command.tossPaymentKey(), command.tossOrderId(), order.getId()))
                .willReturn(false);

        assertThatThrownBy(() -> bfOrderWithdrawService.withdraw(order, 1000, command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FINAL_STAGE_FAILED);
    }

    @Test
    @DisplayName("결제 승인 호출 예외 시 FINAL_STAGE_FAILED 예외")
    void withdraw_whenPaymentClientThrows_throwsFinalStageFailed() {
        Order order = createOrder();
        CreateOrderCommand command = createCommand();
        given(orderPaymentClient.confirmOrder(1000, command.tossPaymentKey(), command.tossOrderId(), order.getId()))
                .willThrow(new RuntimeException("pg timeout"));

        assertThatThrownBy(() -> bfOrderWithdrawService.withdraw(order, 1000, command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FINAL_STAGE_FAILED);
    }

    private Order createOrder() {
        Order order = getOrder();
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        return order;
    }

    private CreateOrderCommand createCommand() {
        return new CreateOrderCommand(
                1L,
                UUID.randomUUID(),
                "요청사항",
                List.of(new OrderProductReq(UUID.randomUUID(), 1)),
                "idempotency-key",
                UUID.randomUUID(),
                "paymentKey",
                "tossOrderId"
        );
    }
}
