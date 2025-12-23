package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.jumunhasyeotjo.order_to_shipping.common.aspect.Metric;
import com.jumunhasyeotjo.order_to_shipping.order.application.CreateOrderSnapshotDto;
import com.jumunhasyeotjo.order_to_shipping.order.application.OrderService;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.CreateOrderCommand;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.RollbackStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BFOrderOrchestrator {

    private final BFSnapshotService bfSnapshotService;
    private final BfReservationService bfReservationService;
    private final BfOrderWithdrawService bfOrderWithdrawService;
    private final BFOrderOutboxService bfOrderOutboxService;
    private final BFOrderService bfOrderService;



    /**
     * 주문 생성 전체 프로세스
     *
     * 흐름:
     * 1. [Tx-1] Request SnapShot 저장 (Order PENDING) → 커밋
     * 2. [Lua Atomic] 재고/쿠폰 예약 + Outbox 발행
     * 3. [Worker 대기] Redis Outbox Worker가 이벤트 처리 완료 대기
     * 4. [Final Stage] 결제 승인 + Outbox/SnapShot 상태 갱신 → 커밋
     * 5. 사용자 응답 반환
     */
    @Metric("주문 생성 전체 프로세스")
    public Order createOrder(CreateOrderCommand command) {
        log.info("[주문 생성 시작] idempotencyKey: {}", command.idempotencyKey());

        // 1. [Tx-1] Request SnapShot 저장 - 별도 트랜잭션
        CreateOrderSnapshotDto snapshotDto = bfSnapshotService.createOrderSnapshot(command);
        Order pendingOrder = snapshotDto.getPendingOrder();
        log.info("[Tx-1 완료] Order 저장 및 커밋 - orderId: {}, status: PENDING", pendingOrder.getId());

        RollbackStatus status = RollbackStatus.NONE;
        try {
            // 2. [Lua Atomic] 재고/쿠폰 예약 + Redis Streams Outbox 발행
            String messageId = bfReservationService.decreaseStockAndUseCoupon(pendingOrder, command);
            log.info("[Lua Atomic 완료] Outbox 발행 - messageId: {}, orderId: {}", messageId, pendingOrder.getId());

            // 해당 지점에서 Crash 발생시 스케줄러를 통해 publishOrderCreatedEvent 보상

            // 3. [Final Stage] 결제 승인
            int paymentPrice = pendingOrder.getTotalPrice() - snapshotDto.getDiscountPrice();
            bfOrderWithdrawService.withdraw(pendingOrder, paymentPrice, command);
            log.info("[Final Stage 완료] 주문 완료 - orderId: {}, status: ORDERED", pendingOrder.getId());

            // 해당 지점에서 Crash 발생시 스케줄러를 통해 아래 로직 재실행 (최종적 일관성 성공 처리)

            // [Tx-1.2] Outbox 상태 갱신 (complete) + SnapShot 상태 갱신 (ORDERED)
            return bfOrderOutboxService.executeStatusUpdate(pendingOrder);

        } catch (Exception e) {
            UUID orderId = pendingOrder.getId();
            log.error("[주문 생성 실패] orderId: {}, error: {}", orderId, e.getMessage(), e);
            log.warn("[Compensate] 주문 실패 롤백 실행 - 단계: {}, 사유: {}", status, e.getMessage());
            bfOrderService.updateStatusForRollback(orderId, status);
            //TODO BF orderRoolledBack으로 변경
            e.printStackTrace();
            throw e;
        }
    }
}