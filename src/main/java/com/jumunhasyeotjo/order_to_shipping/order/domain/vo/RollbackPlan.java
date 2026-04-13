package com.jumunhasyeotjo.order_to_shipping.order.domain.vo;

public record RollbackPlan(
    RollbackStatus status,
    boolean compensateCoupon,
    boolean compensateStock,
    boolean compensatePayment
) {

    public static RollbackPlan useCouponFailure() {
        return new RollbackPlan(RollbackStatus.USE_COUPON, false, true, false);
    }

    public static RollbackPlan decreaseStockFailure(boolean compensateCoupon) {
        return new RollbackPlan(RollbackStatus.DECREASE_STOCK, compensateCoupon, false, false);
    }

    public static RollbackPlan paymentFailure(boolean compensateCoupon) {
        return new RollbackPlan(RollbackStatus.PAYED_ORDER, compensateCoupon, true, true);
    }

    public static RollbackPlan fullRollback() {
        return new RollbackPlan(RollbackStatus.FULL_ROLLBACK, true, true, false);
    }
}
