package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.jumunhasyeotjo.order_to_shipping.common.exception.BusinessException;
import com.jumunhasyeotjo.order_to_shipping.common.exception.ErrorCode;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.CreateOrderCommand;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.OrderProductReq;
import com.jumunhasyeotjo.order_to_shipping.order.application.dto.ProductResult;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.OrderProduct;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.VendorOrder;
import com.jumunhasyeotjo.order_to_shipping.order.domain.event.BfOrderCreatedEvent;
import com.jumunhasyeotjo.order_to_shipping.order.domain.event.BfOrderRolledBackEvent;
import com.jumunhasyeotjo.order_to_shipping.order.domain.repository.OrderRepository;
import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.OrderStatus;
import com.jumunhasyeotjo.order_to_shipping.order.domain.vo.RollbackStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BFOrderService {
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Order createOrderAggregate(CreateOrderCommand command, List<ProductResult> productRes) {
        List<OrderProduct> orderProducts = createOrderProducts(productRes, command.orderProducts());
        List<VendorOrder> orderCompanies = createVendorOrders(productRes, orderProducts);
        return orderCreateAndSave(command, orderCompanies, productRes);
    }

    @Transactional
    public Order updateStatusForRollback(UUID orderId, RollbackStatus rollbackStatus) {
        Order order = getOrder(orderId);
        order.updateStatus(OrderStatus.FAILED);
        eventPublisher.publishEvent(BfOrderRolledBackEvent.of(order.getId(), rollbackStatus));
        return order;
    }

    @Transactional
    public Order updateStatusForComplete(UUID orderId, List<VendorOrder> vendorOrders) {
        Order order = getOrder(orderId);
        order.updateStatus(OrderStatus.ORDERED);
        eventPublisher.publishEvent(BfOrderCreatedEvent.of(order, vendorOrders));
        return order;
    }

    private Order orderCreateAndSave(CreateOrderCommand command, List<VendorOrder> orderCompanies, List<ProductResult> productRes) {
        int totalPrice = calTotalPrice(productRes);
        Order order = Order.create(
                orderCompanies,
                command.userId(),
                command.organizationId(),
                command.requestMessage(),
                totalPrice,
                command.idempotencyKey());
        return orderRepository.save(order);
    }

    private List<OrderProduct> createOrderProducts(List<ProductResult> productResults, List<OrderProductReq> orderProductReqs) {
        Map<UUID, Integer> productIdQuantityMap = orderProductReqs
                .stream()
                .collect(Collectors.toMap(OrderProductReq::productId, OrderProductReq::quantity));

        List<OrderProduct> orderProducts = new ArrayList<>();
        for (ProductResult productRes : productResults) {
            UUID productId = productRes.productId();
            OrderProduct orderProduct = OrderProduct.create(productId, productRes.price(), productIdQuantityMap.get(productId), productRes.name());
            orderProducts.add(orderProduct);
        }
        return orderProducts;
    }

    private List<VendorOrder> createVendorOrders(List<ProductResult> productResults, List<OrderProduct> orderProducts) {
        List<VendorOrder> orderCompanies = new ArrayList<>();
        for (ProductResult productRes : productResults) {
            VendorOrder.create(productRes.companyId(), orderProducts);
            orderCompanies.add(VendorOrder.create(productRes.companyId(), orderProducts));
        }
        return orderCompanies;
    }

    private int calTotalPrice(List<ProductResult> productResults) {
        return productResults
                .stream()
                .mapToInt(ProductResult::price)
                .sum();
    }

    private Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }
}
