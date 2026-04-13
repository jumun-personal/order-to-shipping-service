package com.jumunhasyeotjo.order_to_shipping.coupon.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeotjo.order_to_shipping.coupon.application.CouponCompensationService;
import com.jumunhasyeotjo.order_to_shipping.coupon.application.IssueCouponService;
import com.jumunhasyeotjo.order_to_shipping.coupon.application.command.CancelCouponCommand;
import com.jumunhasyeotjo.order_to_shipping.coupon.application.command.IssueCouponCommand;
import com.jumunhasyeotjo.order_to_shipping.coupon.application.event.CouponIssueEvent;
import com.jumunhasyeotjo.order_to_shipping.coupon.infrastructure.event.OrderCancelledEvent;
import com.jumunhasyeotjo.order_to_shipping.coupon.infrastructure.event.OrderRollbackEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
public class CouponEventConsumer {
    private final CouponCompensationService couponCompensationService;
    private final IssueCouponService issueCouponService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${spring.kafka.topics.order}",
        groupId = "coupon",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void listen(ConsumerRecord<String, String> record) {

        String key = record.key();
        String type = new String(record.headers().lastHeader("eventType").value());
        String value = record.value();

        log.info("Received message: type={}, key={}, value={}", type, key, value);

        try {
            switch (type) {
                case "ISSUE_COUPON" -> handleIssueCoupon(value);
                case "ORDER_ROLLEDBACK" -> handleRollback(value);
                case "ORDER_CANCELLED" -> handleCancel(value);
                default -> log.warn("Unexpected message type: " + type);
            }
        } catch (Exception e) {
            log.error("Failed to process event: {}", type, e);
            throw new RuntimeException(e);
        }
    }

    private void handleIssueCoupon(String json) throws Exception {
        CouponIssueEvent event = objectMapper.readValue(json, CouponIssueEvent.class);

        log.info("[issueCoupon] event: {}", event);

        UUID couponId = event.couponId();
        Long userId = event.userId();

        if (!issueCouponService.existsByCouponIdAndUserId(couponId, userId)) {
            try {
                issueCouponService.issue(
                    new IssueCouponCommand(couponId, userId)
                );
            } catch (DataIntegrityViolationException e) {
                log.warn("중복 발급 시도.");
            }
        } else {
            throw new IllegalArgumentException("이미 발급 받은 유저입니다.");
        }
    }

    private void handleRollback(String json) throws Exception {
        OrderRollbackEvent event = objectMapper.readValue(json, OrderRollbackEvent.class);

        log.info("[rollback] event: {}", event);

        if (!event.compensateCoupon()) {
            log.info("[rollback] coupon compensation skipped. orderId={}", event.orderId());
            return;
        }

        couponCompensationService.requestCompensation(event.orderId());
    }

    private void handleCancel(String json) throws Exception {
        OrderCancelledEvent event = objectMapper.readValue(json, OrderCancelledEvent.class);

        log.info("[cancel] event: {}", event);

        issueCouponService.cancelCoupon(
            new CancelCouponCommand(event.orderId())
        );
    }
}
