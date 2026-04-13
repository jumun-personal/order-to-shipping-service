package com.jumunhasyeotjo.order_to_shipping.coupon.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeotjo.order_to_shipping.coupon.application.CouponCompensationService;
import com.jumunhasyeotjo.order_to_shipping.coupon.application.IssueCouponService;
import com.jumunhasyeotjo.order_to_shipping.coupon.infrastructure.event.OrderRollbackEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponEventConsumerTest {

    @Mock
    private CouponCompensationService couponCompensationService;
    @Mock
    private IssueCouponService issueCouponService;

    private CouponEventConsumer couponEventConsumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        couponEventConsumer = new CouponEventConsumer(couponCompensationService, issueCouponService, objectMapper);
    }

    @Test
    @DisplayName("롤백 이벤트에서 쿠폰 보상 플래그가 켜져 있으면 pending compensation을 요청한다")
    void listen_whenRollbackNeedsCouponCompensation_requestsPendingCompensation() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderRollbackEvent event = new OrderRollbackEvent(
                orderId,
                "DECREASE_STOCK",
                true,
                false,
                false,
                LocalDateTime.now()
        );

        couponEventConsumer.listen(createRollbackRecord(event));

        verify(couponCompensationService).requestCompensation(orderId);
        verify(issueCouponService, never()).cancelCoupon(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("롤백 이벤트에서 쿠폰 보상 플래그가 꺼져 있으면 아무 작업도 하지 않는다")
    void listen_whenRollbackSkipsCouponCompensation_doesNothing() throws Exception {
        OrderRollbackEvent event = new OrderRollbackEvent(
                UUID.randomUUID(),
                "USE_COUPON",
                false,
                true,
                false,
                LocalDateTime.now()
        );

        couponEventConsumer.listen(createRollbackRecord(event));

        verify(couponCompensationService, never()).requestCompensation(org.mockito.ArgumentMatchers.any());
        verify(issueCouponService, never()).cancelCoupon(org.mockito.ArgumentMatchers.any());
    }

    private ConsumerRecord<String, String> createRollbackRecord(OrderRollbackEvent event) throws Exception {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "order",
                0,
                0L,
                event.orderId().toString(),
                objectMapper.writeValueAsString(event)
        );
        record.headers().add("eventType", "ORDER_ROLLEDBACK".getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
