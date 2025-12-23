package com.jumunhasyeotjo.order_to_shipping.payment.infrastructure.external.toss_payment;

import com.jumunhasyeotjo.order_to_shipping.payment.infrastructure.external.toss_payment.dto.TossCancelRequest;
import com.jumunhasyeotjo.order_to_shipping.payment.infrastructure.external.toss_payment.dto.TossConfirmRequest;
import com.jumunhasyeotjo.order_to_shipping.payment.infrastructure.external.toss_payment.dto.TossPaymentResponse;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Profile("perf")
@Primary
@Component
public class MockTossPaymentsClient implements TossPaymentsClient {

    @Override
    public TossPaymentResponse confirm(TossConfirmRequest request) {
        return mockConfirmResponse(request);
    }

    @Override
    public TossPaymentResponse cancel(String paymentKey, TossCancelRequest request) {
        return mockCancelResponse(paymentKey);
    }

    @Override
    public TossPaymentResponse getPaymentInfo(String paymentKey) {
        return mockGetPaymentInfo(paymentKey);
    }

    private TossPaymentResponse mockConfirmResponse(TossConfirmRequest request) {
        // 지연테스트
//        try {
//            Thread.sleep(ThreadLocalRandom.current().nextInt(300, 501));
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }

        return TossPaymentResponse.builder()
            .paymentKey(request.getPaymentKey())
            .orderId(request.getOrderId())
            .status("DONE")
            .method("CARD")
            .approvedAt(OffsetDateTime.now())
            .totalAmount(request.getAmount())
            .build();
    }

    private TossPaymentResponse mockCancelResponse(String paymentKey) {
        // 지연테스트
//        try {
//            Thread.sleep(ThreadLocalRandom.current().nextInt(300, 501));
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }

        return TossPaymentResponse.builder()
            .paymentKey(paymentKey)
            .status("CANCELED")
            .approvedAt(OffsetDateTime.now())
            .build();
    }

    private TossPaymentResponse mockGetPaymentInfo(String paymentKey) {
        // 지연테스트
//        try {
//            Thread.sleep(ThreadLocalRandom.current().nextInt(300, 501));
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }

        return TossPaymentResponse.builder()
            .paymentKey(paymentKey)
            .status("DONE")
            .build();
    }
}