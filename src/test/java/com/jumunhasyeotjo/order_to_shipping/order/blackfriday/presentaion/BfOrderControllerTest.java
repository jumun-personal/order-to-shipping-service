package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.presentaion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeotjo.order_to_shipping.config.MockPassportArgumentResolver;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.CreateOrderCommand;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.OrderProductReq;
import com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion.BFOrderOrchestrator;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.OrderStatus;
import com.jumunhasyeotjo.order_to_shipping.order.presentation.dto.request.CreateOrderReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static com.jumunhasyeotjo.order_to_shipping.order.fixtures.OrderFixtures.getOrder;
import static com.library.passport.proto.PassportProto.Passport;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BfOrderControllerTest {

    @InjectMocks
    private BfOrderController bfOrderController;

    @Mock
    private BFOrderOrchestrator bfOrderOrchestrator;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Passport passport;

    @BeforeEach
    void setUp() {
        passport = Passport.newBuilder()
                .setUserId(1L)
                .setBelong(UUID.randomUUID().toString())
                .setRole("COMPANY_MANAGER")
                .setName("TEST")
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(bfOrderController)
                .setCustomArgumentResolvers(new MockPassportArgumentResolver(passport))
                .build();
    }

    @Test
    @DisplayName("BF 주문 생성 API 성공")
    void createOrder_success() throws Exception {
        CreateOrderReq req = new CreateOrderReq(
                "요청사항",
                List.of(new OrderProductReq(UUID.randomUUID(), 1)),
                UUID.randomUUID(),
                "paymentKey",
                "tossOrderId"
        );
        String idempotencyKey = "bf-idempotency-key";

        Order response = getOrder();
        UUID orderId = UUID.randomUUID();
        ReflectionTestUtils.setField(response, "id", orderId);
        response.updateStatus(OrderStatus.ORDERED);

        given(bfOrderOrchestrator.createOrder(any())).willReturn(response);

        mockMvc.perform(post("/api/v1/orders/bf")
                        .header("x-idempotency-key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.status").value(OrderStatus.ORDERED.toString()));

        ArgumentCaptor<CreateOrderCommand> commandCaptor = ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(bfOrderOrchestrator).createOrder(commandCaptor.capture());
        CreateOrderCommand captured = commandCaptor.getValue();

        org.assertj.core.api.Assertions.assertThat(captured.idempotencyKey()).isEqualTo(idempotencyKey);
        org.assertj.core.api.Assertions.assertThat(captured.userId()).isEqualTo(passport.getUserId());
        org.assertj.core.api.Assertions.assertThat(captured.organizationId())
                .isEqualTo(UUID.fromString(passport.getBelong()));
        org.assertj.core.api.Assertions.assertThat(captured.orderProducts()).hasSize(1);
    }
}
