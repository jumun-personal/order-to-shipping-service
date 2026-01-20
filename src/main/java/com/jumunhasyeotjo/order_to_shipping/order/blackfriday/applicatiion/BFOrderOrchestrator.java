package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.jumunhasyeotjo.order_to_shipping.order.application.CreateOrderSnapshotDto;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.CreateOrderCommand;
import com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion.outbox.BFOrderOutboxService;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.RollbackStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
     * [Main Thread]
     * 1. [Tx-1] Request SnapShot 저장 (Order PENDING) → 커밋
     * 2. [Lua Atomic] 재고/쿠폰 예약 + RedisStreams Outbox 발행
     * 4. [Tx-2] 결제 승인 + Outbox/SnapShot 상태 갱신 → 커밋
     * 5. 사용자 응답 반환
     * [Worker Thread]
     * 1. RedisOutbox Worker가 처리 완료 대상 XREADGROUP 후 이벤트 발행
     */
    public Order createOrder(CreateOrderCommand command) {
        log.debug("[주문 생성 시작] idempotencyKey: {}", command.idempotencyKey());
        // 1. [Tx-1] Request SnapShot 저장 - 별도 트랜잭션
        CreateOrderSnapshotDto snapshotDto = bfSnapshotService.createOrderSnapshot(command);
        Order pendingOrder = snapshotDto.getPendingOrder();
        log.debug("[Tx-1 완료] Order 저장 및 커밋 - orderId: {}, status: PENDING", pendingOrder.getId());

        RollbackStatus status = RollbackStatus.NONE;

        try {
            // 2. [Lua Atomic] 재고/쿠폰 예약 + Redis Streams Outbox 발행
            String messageId = bfReservationService.decreaseStockAndUseCoupon(pendingOrder, command);
            log.debug("[Lua Atomic 완료] Outbox 발행 - messageId: {}, orderId: {}", messageId, pendingOrder.getId());

            // 3. [Final Stage] 결제 승인
            int paymentPrice = pendingOrder.getTotalPrice() - snapshotDto.getDiscountPrice();
            bfOrderWithdrawService.withdraw(pendingOrder, paymentPrice, command);
            log.debug("[Final Stage 완료] 주문 완료 - orderId: {}, status: ORDERED", pendingOrder.getId());

            // [Tx-2] Outbox 상태 갱신 (complete) + SnapShot 상태 갱신 (ORDERED)
            return bfOrderOutboxService.executeStatusUpdate(pendingOrder, command);

        } catch (Exception e) {
            log.error("[주문 생성 실패] orderId: {}, error: {}", pendingOrder.getId(), e.getMessage(), e);
            bfOrderService.updateStatusForRollback(pendingOrder.getId(), status);
            throw e;
        }
    }
}