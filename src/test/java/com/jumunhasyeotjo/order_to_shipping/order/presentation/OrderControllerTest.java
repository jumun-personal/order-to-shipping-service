package com.jumunhasyeotjo.order_to_shipping.order.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeotjo.order_to_shipping.config.MockPassportArgumentResolver;
import com.jumunhasyeotjo.order_to_shipping.order.application.OrderOrchestrator;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.OrderProductReq;
import com.jumunhasyeotjo.order_to_shipping.order.application.dto.OrderResult;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.CancelReason;
import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.OrderStatus;
import com.jumunhasyeotjo.order_to_shipping.order.presentation.dto.request.CancelOrderReq;
import com.jumunhasyeotjo.order_to_shipping.order.presentation.dto.request.CreateOrderReq;
import com.jumunhasyeotjo.order_to_shipping.order.presentation.dto.request.OrderUpdateStatusReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.jumunhasyeotjo.order_to_shipping.order.fixtures.OrderFixtures.getOrder;
import static com.library.passport.proto.PassportProto.Passport;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@ExtendWith(MockitoExtension.class)
public class OrderControllerTest {

    @InjectMocks
    private OrderController orderController;

    @Mock
    private OrderOrchestrator orderOrchestrator;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        Passport mockPassport = Passport.newBuilder()
                .setUserId(1L)
                .setBelong(UUID.randomUUID().toString()) // companyId 등
                .setRole("MANAGER")
                .setName("TEST")
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setCustomArgumentResolvers(new MockPassportArgumentResolver(mockPassport),
                        new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("주문 생성 API")
    void createOrder_ValidRequest_ReturnsCreatedOrder() throws Exception {
        // given
        List<OrderProductReq> orderProducts = new ArrayList<>();
        orderProducts.add(new OrderProductReq(UUID.randomUUID(), 1));

        CreateOrderReq request = new CreateOrderReq("요청사항", orderProducts, UUID.randomUUID(), "paymentKey", "tossOrderId");

        Order response = getOrder();

        given(orderOrchestrator.createOrder(any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/orders")
                        .header("x-idempotency-key", "멱등키")
                        .contentType(String.valueOf(MediaType.APPLICATION_JSON))
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value(OrderStatus.PENDING.toString()));
    }

    @Test
    @DisplayName("주문 상태 변경 API")
    void updateOrderStatus_ValidRequest_ReturnsUpdatedOrder() throws Exception {
        // given
        OrderUpdateStatusReq request = new OrderUpdateStatusReq(OrderStatus.DONE);

        Order response = getOrder();
        ReflectionTestUtils.setField(response, "status", OrderStatus.DONE);

        given(orderOrchestrator.updateOrderStatus(any())).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/orders/{orderId}", UUID.randomUUID())
                        .contentType(String.valueOf(MediaType.APPLICATION_JSON))
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(OrderStatus.DONE.toString()));
    }

    @Test
    @DisplayName("주문 취소 API")
    void cancelOrder_ValidOrderId_ReturnsCancelledOrder() throws Exception {
        // given
        Order response = getOrder();
        CancelOrderReq request = new CancelOrderReq(CancelReason.ETC);
        ReflectionTestUtils.setField(response, "status", OrderStatus.CANCELLED);

        given(orderOrchestrator.cancelOrder(any())).willReturn(response);

        // when & then
        mockMvc.perform(delete("/api/v1/orders/{orderId}", UUID.randomUUID())
                        .contentType(String.valueOf(MediaType.APPLICATION_JSON))
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(OrderStatus.CANCELLED.toString()));
    }

    @Test
    @DisplayName("주문 단건 조회 API")
    void getOrder_ValidOrderId_ReturnsOrder() throws Exception {
        // given
        Order order = getOrder();
        OrderResult response = OrderResult.of(order);

        given(orderOrchestrator.getOrder(any())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/orders/{orderId}", UUID.randomUUID())
                        .contentType(String.valueOf(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestMessage").value(order.getRequestMessage()))
                .andExpect(jsonPath("$.data.totalPrice").value(order.getTotalPrice()))
                .andExpect(jsonPath("$.data.status").value(order.getStatus().toString()))
                .andExpect(jsonPath("$.data.orderProducts", hasSize(1)));
    }

    @Test
    @DisplayName("주문 다건 조회 API")
    void searchOrder_ValidParams_ReturnsOrderPage() throws Exception {
        // given
        OrderResult orderResult1 = OrderResult.of(getOrder());
        OrderResult orderResult2 = OrderResult.of(getOrder());
        OrderResult orderResult3 = OrderResult.of(getOrder());

        Page<OrderResult> response = new PageImpl<>(List.of(orderResult1, orderResult2, orderResult3),
                PageRequest.of(0, 10), 0);

        given(orderOrchestrator.searchOrder(any())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/orders")
                        .param("companyId", String.valueOf(UUID.randomUUID()))
                        .param("page", "0")
                        .param("size", "10")
                        .accept(String.valueOf(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(3));
    }
}
