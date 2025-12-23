package com.jumunhasyeotjo.order_to_shipping.payment.application;

import com.jumunhasyeotjo.order_to_shipping.common.aspect.Metric;
import com.jumunhasyeotjo.order_to_shipping.payment.infrastructure.external.toss_payment.MockTossPaymentsClient;
import org.springframework.stereotype.Service;

import com.jumunhasyeotjo.order_to_shipping.common.exception.BusinessException;
import com.jumunhasyeotjo.order_to_shipping.common.exception.ErrorCode;
import com.jumunhasyeotjo.order_to_shipping.common.exception.TossRemoteException;
import com.jumunhasyeotjo.order_to_shipping.payment.domain.entity.Payment;
import com.jumunhasyeotjo.order_to_shipping.payment.infrastructure.external.toss_payment.TossPaymentsClient;
import com.jumunhasyeotjo.order_to_shipping.payment.infrastructure.external.toss_payment.dto.TossCancelRequest;
import com.jumunhasyeotjo.order_to_shipping.payment.infrastructure.external.toss_payment.dto.TossConfirmRequest;
import com.jumunhasyeotjo.order_to_shipping.payment.infrastructure.external.toss_payment.dto.TossPaymentResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TossPaymentService {

	private final MockTossPaymentsClient tossPaymentsClient;

	@Metric(value = "PG사 결제 처리 프로세스", level = Metric.Level.DEBUG)
	public TossPaymentResponse confirm(TossConfirmRequest tossConfirmRequest, Payment payment) {
		try {
			log.info("[PAY] Toss pay confirm request");
			return tossPaymentsClient.confirm(tossConfirmRequest);
		} catch (TossRemoteException e) {
			log.warn("[PAY] Toss confirm failed: status={}, code={}, msg={}",
				e.getStatus(), e.getCode(), e.getMessage());

			String clientMsg = e.getTossMessage() != null ? e.getTossMessage() : "결제 처리 중 오류가 발생했습니다.";
			String clientCode = e.getCode() != null ? e.getCode() : "TOSS_UNKNOWN_ERROR";
			payment.failPayment(clientCode, clientMsg);

			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
		}
	}

	public TossPaymentResponse getPaymentInfo(String tossPaymentKey) {
		try {
			log.info("[PAY] Toss pay getPaymentInfo request");
			return tossPaymentsClient.getPaymentInfo(tossPaymentKey);
		} catch (TossRemoteException e) {
			log.warn("[PAY] Toss getPaymentInfo failed: status={}, code={}, msg={}", e.getStatus(), e.getCode(),
				e.getMessage());

			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
		}
	}


	public TossPaymentResponse cancel(String cancelReason, Payment payment) {
		try {
			log.info("[PAY] Toss pay cancel request");
			TossPaymentResponse res = tossPaymentsClient.cancel(payment.getTossPaymentKey(), TossCancelRequest.of(cancelReason));
			payment.cancel(cancelReason, res.getCancels().get(0).getCanceledAt());

			return res;
		} catch (TossRemoteException e) {
			log.warn("[PAY] Toss cacel failed: status={}, code={}, msg={}", e.getStatus(), e.getCode(),
				e.getMessage());

			String clientMsg = e.getTossMessage() != null ? e.getTossMessage() : "결제 취소 중 오류가 발생했습니다.";
			String clientCode = e.getCode() != null ? e.getCode() : "TOSS_UNKNOWN_ERROR";

			payment.failPayment(clientCode, clientMsg);

			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
		}
	}
}
