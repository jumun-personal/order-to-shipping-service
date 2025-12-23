package com.jumunhasyeotjo.order_to_shipping.order.presentation;

import static org.junit.jupiter.api.Assertions.*;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.OrderProductReq;
import com.jumunhasyeotjo.order_to_shipping.order.presentation.dto.request.CreateOrderReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("BfOrderController 통합 테스트")
class BfOrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID testProductId;
    private String testIdempotencyKey;
    private CreateOrderReq testRequest;

    @BeforeEach
    void setUp() {
        testProductId = UUID.randomUUID();
        testIdempotencyKey = UUID.randomUUID().toString();

        testRequest = new CreateOrderReq(
                "통합 테스트 주문",
                List.of(new OrderProductReq(testProductId, 5)),
                null,
                "integration_test_payment_key",
                "integration_test_order_id"
        );
    }

    @Test
    @DisplayName("통합 테스트: 전체 주문 생성 플로우")
    void createOrder_IntegrationTest() throws Exception {
        // when
        ResultActions result = mockMvc.perform(post("/api/v1/bf/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Passport", createTestPassportHeader())
                .header("x-idempotency-key", testIdempotencyKey)
                .content(objectMapper.writeValueAsString(testRequest)));

        // then
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").exists())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andDo(print());
    }

    @Test
    @DisplayName("통합 테스트: Redis Outbox 발행 확인")
    void createOrder_RedisOutboxPublished() throws Exception {
        // given
        String uniqueIdempotencyKey = UUID.randomUUID().toString();

        // when
        ResultActions result = mockMvc.perform(post("/api/v1/bf/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Passport", createTestPassportHeader())
                .header("x-idempotency-key", uniqueIdempotencyKey)
                .content(objectMapper.writeValueAsString(testRequest)));

        // then
        result.andExpect(status().isCreated())
                .andDo(print());

        // TODO: Redis에서 Outbox 이벤트가 발행되었는지 확인
        // RedisTemplate을 사용하여 검증
    }

    @Test
    @DisplayName("통합 테스트: DB Outbox 생성 확인")
    void createOrder_DbOutboxCreated() throws Exception {
        // given
        String uniqueIdempotencyKey = UUID.randomUUID().toString();

        // when
        ResultActions result = mockMvc.perform(post("/api/v1/bf/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Passport", createTestPassportHeader())
                .header("x-idempotency-key", uniqueIdempotencyKey)
                .content(objectMapper.writeValueAsString(testRequest)));

        // then
        result.andExpect(status().isCreated())
                .andDo(print());

        // TODO: DB에서 Outbox 레코드가 생성되었는지 확인
        // OutboxRepository를 사용하여 검증
    }

    private String createTestPassportHeader() {
        // 실제 Passport 생성 로직 구현
        return "test_passport_header";
    }
}