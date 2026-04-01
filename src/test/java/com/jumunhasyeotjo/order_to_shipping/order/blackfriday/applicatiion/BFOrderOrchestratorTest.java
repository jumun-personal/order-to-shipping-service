package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.jumunhasyeotjo.order_to_shipping.common.exception.BusinessException;
import com.jumunhasyeotjo.order_to_shipping.common.exception.ErrorCode;
import com.jumunhasyeotjo.order_to_shipping.order.application.CreateOrderSnapshotDto;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.CreateOrderCommand;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.OrderProductReq;
import com.jumunhasyeotjo.order_to_shipping.order.application.dto.ExternalExists;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderCouponClient;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderStockClient;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.RollbackStatus;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BFOrderOrchestratorTest {

    @Mock
    private BFOrderService bfOrderService;
    @Mock
    private BFSnapshotService bfSnapshotService;
    @Mock
    private BFOrderWithdrawService bfOrderWithdrawService;
    @Mock
    private OrderCouponClient orderCouponClient;
    @Mock
    private OrderStockClient orderStockClient;

    @InjectMocks
    private BFOrderOrchestrator bfOrderOrchestrator;

    @Test
    @DisplayName("BF 주문 생성 성공 - 쿠폰 사용 포함")
    void createOrder_withCoupon_success() {
        CreateOrderCommand command = createCommand(UUID.randomUUID());
        Order pendingOrder = createPendingOrder(1000);
        Order completedOrder = createPendingOrder(1000);

        given(bfSnapshotService.createOrderSnapshot(command))
                .willReturn(new CreateOrderSnapshotDto(pendingOrder, 100));
        given(orderCouponClient.useCoupon(command.couponId(), pendingOrder.getId())).willReturn(100);
        given(orderStockClient.decreaseStock(command.orderProducts(), pendingOrder.getId().toString()))
                .willReturn(new ExternalExists(true));
        given(bfOrderService.updateStatusForComplete(pendingOrder.getId(), command)).willReturn(completedOrder);

        Order result = bfOrderOrchestrator.createOrder(command);

        assertThat(result).isEqualTo(completedOrder);
        verify(bfSnapshotService).createOrderSnapshot(command);
        verify(orderCouponClient).useCoupon(command.couponId(), pendingOrder.getId());
        verify(orderStockClient).decreaseStock(command.orderProducts(), pendingOrder.getId().toString());
        verify(bfOrderWithdrawService).withdraw(pendingOrder, 900, command);
        verify(bfOrderService).updateStatusForComplete(pendingOrder.getId(), command);
        verify(bfOrderService, never()).updateStatusForRollback(any(), any());
    }

    @Test
    @DisplayName("BF 주문 생성 성공 - 쿠폰 없이 진행")
    void createOrder_withoutCoupon_success() {
        CreateOrderCommand command = createCommand(null);
        Order pendingOrder = createPendingOrder(1000);

        given(bfSnapshotService.createOrderSnapshot(command))
                .willReturn(new CreateOrderSnapshotDto(pendingOrder, null));
        given(orderStockClient.decreaseStock(command.orderProducts(), pendingOrder.getId().toString()))
                .willReturn(new ExternalExists(true));
        given(bfOrderService.updateStatusForComplete(pendingOrder.getId(), command)).willReturn(pendingOrder);

        bfOrderOrchestrator.createOrder(command);

        verify(bfSnapshotService).createOrderSnapshot(command);
        verify(orderCouponClient, never()).useCoupon(any(), any());
        verify(bfOrderWithdrawService).withdraw(pendingOrder, 1000, command);
    }

    @Test
    @DisplayName("BF 주문 생성 실패 - snapshot 단계 실패는 그대로 전파된다")
    void createOrder_whenSnapshotFails_throwsException() {
        CreateOrderCommand command = createCommand(UUID.randomUUID());
        BusinessException failure = new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        given(bfSnapshotService.createOrderSnapshot(command)).willThrow(failure);

        assertThatThrownBy(() -> bfOrderOrchestrator.createOrder(command))
                .isSameAs(failure);

        verify(bfOrderService, never()).updateStatusForRollback(any(), any());
        verify(orderCouponClient, never()).useCoupon(any(), any());
        verify(orderStockClient, never()).decreaseStock(any(), any());
    }

    @Test
    @DisplayName("BF 주문 생성 실패 - 쿠폰 사용 실패 시 롤백 상태는 USE_COUPON")
    void createOrder_whenCouponUseFails_rollbackUseCoupon() {
        CreateOrderCommand command = createCommand(UUID.randomUUID());
        Order pendingOrder = createPendingOrder(1000);

        given(bfSnapshotService.createOrderSnapshot(command))
                .willReturn(new CreateOrderSnapshotDto(pendingOrder, 100));
        given(orderCouponClient.useCoupon(command.couponId(), pendingOrder.getId())).willReturn(0);

        assertThatThrownBy(() -> bfOrderOrchestrator.createOrder(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(bfOrderService).updateStatusForRollback(pendingOrder.getId(), RollbackStatus.USE_COUPON);
        verify(orderStockClient, never()).decreaseStock(any(), any());
    }

    @Test
    @DisplayName("BF 주문 생성 실패 - 재고 부족 시 롤백 상태는 DECREASE_STOCK")
    void createOrder_whenStockDecreaseFails_rollbackDecreaseStock() {
        CreateOrderCommand command = createCommand(null);
        Order pendingOrder = createPendingOrder(1000);

        given(bfSnapshotService.createOrderSnapshot(command))
                .willReturn(new CreateOrderSnapshotDto(pendingOrder, null));
        given(orderStockClient.decreaseStock(command.orderProducts(), pendingOrder.getId().toString()))
                .willReturn(new ExternalExists(false));

        assertThatThrownBy(() -> bfOrderOrchestrator.createOrder(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PRODUCT_STOCK);

        verify(bfOrderService).updateStatusForRollback(pendingOrder.getId(), RollbackStatus.DECREASE_STOCK);
        verify(bfOrderWithdrawService, never()).withdraw(any(), anyInt(), any());
    }

    @Test
    @DisplayName("BF 주문 생성 실패 - 결제 실패 시 롤백 상태는 PAYED_ORDER")
    void createOrder_whenWithdrawFails_rollbackPayedOrder() {
        CreateOrderCommand command = createCommand(null);
        Order pendingOrder = createPendingOrder(1000);

        given(bfSnapshotService.createOrderSnapshot(command))
                .willReturn(new CreateOrderSnapshotDto(pendingOrder, null));
        given(orderStockClient.decreaseStock(command.orderProducts(), pendingOrder.getId().toString()))
                .willReturn(new ExternalExists(true));

        BusinessException paymentFailure = new BusinessException(ErrorCode.FINAL_STAGE_FAILED);
        givenExceptionOnWithdraw(pendingOrder, command, paymentFailure);

        assertThatThrownBy(() -> bfOrderOrchestrator.createOrder(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FINAL_STAGE_FAILED);

        verify(bfOrderService).updateStatusForRollback(pendingOrder.getId(), RollbackStatus.PAYED_ORDER);
    }

    private void givenExceptionOnWithdraw(Order pendingOrder, CreateOrderCommand command, RuntimeException e) {
        org.mockito.Mockito.doThrow(e)
                .when(bfOrderWithdrawService)
                .withdraw(pendingOrder, 1000, command);
    }

    private Order createPendingOrder(int totalPrice) {
        Order order = getOrder();
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(order, "totalPrice", totalPrice);
        return order;
    }

    private CreateOrderCommand createCommand(UUID couponId) {
        return new CreateOrderCommand(
                1L,
                UUID.randomUUID(),
                "요청사항",
                List.of(new OrderProductReq(UUID.randomUUID(), 1)),
                "idempotency-key",
                couponId,
                "paymentKey",
                "tossOrderId"
        );
    }
}
