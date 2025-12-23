package com.jumunhasyeotjo.order_to_shipping.order.application;

import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import lombok.Getter;

@Getter
public class CreateOrderSnapshotDto{
    private final Order pendingOrder;
    private final Integer discountPrice;

    public CreateOrderSnapshotDto(Order pendingOrder, Integer discountPrice) {
        this.pendingOrder = pendingOrder;
        this.discountPrice = discountPrice;
    }
}
