package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.jumunhasyeotjo.order_to_shipping.common.exception.BusinessException;
import com.jumunhasyeotjo.order_to_shipping.common.exception.ErrorCode;
import com.jumunhasyeotjo.order_to_shipping.coupon.presentation.dto.res.CouponRes;
import com.jumunhasyeotjo.order_to_shipping.order.application.CreateOrderSnapshotDto;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.CreateOrderCommand;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.OrderProductReq;
import com.jumunhasyeotjo.order_to_shipping.order.application.dto.ProductListRes;
import com.jumunhasyeotjo.order_to_shipping.order.application.dto.ProductResult;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderCouponClient;
import com.jumunhasyeotjo.order_to_shipping.order.application.service.OrderProductClient;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import static com.jumunhasyeotjo.order_to_shipping.order.fixtures.OrderFixtures.getOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BFSnapshotServiceTest {

    @Mock
    private BFOrderValidService validService;
    @Mock
    private BFOrderService bfOrderService;
    @Mock
    private OrderProductClient orderProductClient;
    @Mock
    private OrderCouponClient orderCouponClient;

    private BFSnapshotService bfSnapshotService;
    private ExecutorService ioExecutor;

    @BeforeEach
    void setUp() {
        ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
        bfSnapshotService = new BFSnapshotService(
                validService,
                bfOrderService,
                orderProductClient,
                orderCouponClient,
                ioExecutor
        );
        ReflectionTestUtils.setField(bfSnapshotService, "prevalidationTimeout", Duration.ofSeconds(3));
    }

    @AfterEach
    void tearDown() throws Exception {
        ioExecutor.close();
    }

    @Test
    @DisplayName("쿠폰이 있는 BF 주문 snapshot 생성 시 병렬 검증과 조회를 모두 수행한다")
    void createOrderSnapshot_withCoupon_success() {
        CreateOrderCommand command = createCommand(UUID.randomUUID());
        List<ProductResult> productResults = productResults(command);
        CouponRes couponRes = couponRes(command.couponId(), 300);
        Order pendingOrder = createPendingOrder(1000);

        given(orderProductClient.findAllProducts(anyList())).willReturn(new ProductListRes(productResults));
        given(orderCouponClient.findCoupon(command.couponId())).willReturn(couponRes);
        given(bfOrderService.createOrderAggregate(command, productResults)).willReturn(pendingOrder);

        CreateOrderSnapshotDto result = bfSnapshotService.createOrderSnapshot(command);

        assertThat(result.getPendingOrder()).isEqualTo(pendingOrder);
        assertThat(result.getDiscountPrice()).isEqualTo(300);
        verify(validService).validateCompany(command.organizationId());
        verify(validService).validateDuplicateOrder(command.idempotencyKey());
        verify(validService).validateCoupon(command.userId(), command.couponId());
        verify(orderCouponClient).findCoupon(command.couponId());
        verify(orderProductClient).findAllProducts(List.of(command.orderProducts().get(0).productId()));
        verify(bfOrderService).createOrderAggregate(command, productResults);
    }

    @Test
    @DisplayName("쿠폰이 없는 BF 주문 snapshot 생성 시 쿠폰 관련 작업은 생성하지 않는다")
    void createOrderSnapshot_withoutCoupon_success() {
        CreateOrderCommand command = createCommand(null);
        List<ProductResult> productResults = productResults(command);
        Order pendingOrder = createPendingOrder(1000);

        given(orderProductClient.findAllProducts(anyList())).willReturn(new ProductListRes(productResults));
        given(bfOrderService.createOrderAggregate(command, productResults)).willReturn(pendingOrder);

        CreateOrderSnapshotDto result = bfSnapshotService.createOrderSnapshot(command);

        assertThat(result.getPendingOrder()).isEqualTo(pendingOrder);
        assertThat(result.getDiscountPrice()).isNull();
        verify(validService).validateCompany(command.organizationId());
        verify(validService).validateDuplicateOrder(command.idempotencyKey());
        verify(validService, never()).validateCoupon(command.userId(), null);
        verify(orderCouponClient, never()).findCoupon(null);
    }

    @Test
    @DisplayName("사전 검증의 첫 BusinessException은 그대로 전파한다")
    void createOrderSnapshot_whenDuplicateOrderFails_propagatesSameException() {
        CreateOrderCommand command = createCommand(null);
        BusinessException duplicateOrder = new BusinessException(ErrorCode.DUPLICATE_ORDER);

        doAnswer(invocation -> {
            throw duplicateOrder;
        }).when(validService).validateDuplicateOrder(command.idempotencyKey());
        given(orderProductClient.findAllProducts(anyList())).willReturn(new ProductListRes(productResults(command)));

        Throwable thrown = catchThrowable(() -> bfSnapshotService.createOrderSnapshot(command));

        assertThat(thrown).isSameAs(duplicateOrder);
        verify(bfOrderService, never()).createOrderAggregate(any(), anyList());
    }

    @Test
    @DisplayName("상품 조회 결과가 요청과 다르면 PRODUCT_NOT_FOUND를 그대로 전파한다")
    void createOrderSnapshot_whenProductMismatch_throwsProductNotFound() {
        CreateOrderCommand command = createCommand(null);
        given(orderProductClient.findAllProducts(anyList())).willReturn(new ProductListRes(List.of()));

        Throwable thrown = catchThrowable(() -> bfSnapshotService.createOrderSnapshot(command));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        verify(bfOrderService, never()).createOrderAggregate(any(), anyList());
    }

    @Test
    @DisplayName("사전 검증이 timeout 되면 ORDER_VALIDATION_FAILED로 감싼다")
    void createOrderSnapshot_whenTimedOut_wrapsFailure() {
        CreateOrderCommand command = createCommand(null);
        ReflectionTestUtils.setField(bfSnapshotService, "prevalidationTimeout", Duration.ofMillis(50));

        given(orderProductClient.findAllProducts(anyList())).willAnswer(invocation -> {
            Thread.sleep(500);
            return new ProductListRes(productResults(command));
        });

        Throwable thrown = catchThrowable(() -> bfSnapshotService.createOrderSnapshot(command));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.ORDER_VALIDATION_FAILED);
        assertThat(thrown.getCause()).isInstanceOf(TimeoutException.class);
        verify(bfOrderService, never()).createOrderAggregate(any(), anyList());
    }

    private Order createPendingOrder(int totalPrice) {
        Order order = getOrder();
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(order, "totalPrice", totalPrice);
        return order;
    }

    private List<ProductResult> productResults(CreateOrderCommand command) {
        OrderProductReq req = command.orderProducts().get(0);
        return List.of(new ProductResult(req.productId(), UUID.randomUUID(), "상품", 1000));
    }

    private CouponRes couponRes(UUID couponId, int discountAmount) {
        return new CouponRes(
                couponId,
                "블프쿠폰",
                discountAmount,
                100,
                10,
                LocalDate.now(),
                LocalDate.now().plusDays(1),
                LocalDateTime.now()
        );
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
