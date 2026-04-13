package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.jumunhasyeotjo.order_to_shipping.common.exception.BusinessException;
import com.jumunhasyeotjo.order_to_shipping.common.exception.ErrorCode;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.CreateOrderCommand;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.OrderProductReq;
import com.jumunhasyeotjo.order_to_shipping.order.application.dto.ProductResult;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import com.jumunhasyeotjo.order_to_shipping.order.domain.event.OrderCreatedEvent;
import com.jumunhasyeotjo.order_to_shipping.order.domain.event.OrderRolledBackEvent;
import com.jumunhasyeotjo.order_to_shipping.order.domain.repository.OrderRepository;
import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.OrderStatus;
import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.RollbackPlan;
import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.RollbackStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BFOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BFOrderService bfOrderService;

    @Test
    @DisplayName("BF 주문 aggregate 생성 시 저장된 Order를 반환한다")
    void createOrderAggregate_success() {
        CreateOrderCommand command = createCommand();
        List<ProductResult> productResults = List.of(
                new ProductResult(command.orderProducts().get(0).productId(), UUID.randomUUID(), "상품", 1000)
        );

        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
            return order;
        });

        Order saved = bfOrderService.createOrderAggregate(command, productResults);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getIdempotencyKey()).isEqualTo(command.idempotencyKey());
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("주문 완료 상태 갱신 시 ORDERED 상태와 생성 이벤트를 발행한다")
    void updateStatusForComplete_success() {
        UUID orderId = UUID.randomUUID();
        Order order = createPendingOrder(orderId);
        CreateOrderCommand command = createCommand();

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        Order result = bfOrderService.updateStatusForComplete(orderId, command);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.ORDERED);
        verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
    }

    @Test
    @DisplayName("주문 실패 상태 갱신 시 FAILED 상태와 롤백 이벤트를 발행한다")
    void updateStatusForRollback_success() {
        UUID orderId = UUID.randomUUID();
        Order order = createPendingOrder(orderId);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        Order result = bfOrderService.updateStatusForRollback(orderId, RollbackPlan.decreaseStockFailure(true));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(eventPublisher).publishEvent(any(OrderRolledBackEvent.class));
    }

    @Test
    @DisplayName("존재하지 않는 주문 상태 갱신 시 ORDER_NOT_FOUND 예외")
    void updateStatus_whenOrderNotFound_throwsException() {
        UUID orderId = UUID.randomUUID();
        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bfOrderService.updateStatusForComplete(orderId, createCommand()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    private Order createPendingOrder(UUID orderId) {
        Order order = Order.create(
                List.of(com.jumunhasyeotjo.order_to_shipping.order.domain.entity.VendorOrder.create(
                        UUID.randomUUID(),
                        List.of(com.jumunhasyeotjo.order_to_shipping.order.domain.entity.OrderProduct.create(
                                UUID.randomUUID(), 1000, 1, "상품"
                        ))
                )),
                1L,
                UUID.randomUUID(),
                "요청사항",
                1000,
                "idempotency-key"
        );
        ReflectionTestUtils.setField(order, "id", orderId);
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
