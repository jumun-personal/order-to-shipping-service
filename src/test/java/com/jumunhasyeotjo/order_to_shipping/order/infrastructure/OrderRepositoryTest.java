package com.jumunhasyeotjo.order_to_shipping.order.infrastructure;

import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.VendorOrder;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.OrderProduct;
import com.jumunhasyeotjo.order_to_shipping.order.infrastructure.repository.JpaOrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class OrderRepositoryTest {

    @Autowired
    private JpaOrderRepository jpaOrderRepository;

    @Autowired
    private EntityManager em;

     UUID companyId = UUID.randomUUID();
     UUID product1Id = UUID.randomUUID();
     UUID product2Id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Order order1 = getOrder(companyId, getOrderCompanies(getOrderProducts(product1Id, 1000, 2, "상품1")), "주문1", 2000);
        Order order2 = getOrder(companyId, getOrderCompanies(getOrderProducts(product2Id,3000, 5, "상품2")), "주문2", 15000);
        Order order3 = getOrder(companyId, getOrderCompanies(getOrderProducts(product1Id, 1000, 1, "상품3")), "주문3", 1000);
        Order order4 = getOrder(companyId, getOrderCompanies(getOrderProducts(product2Id,3000, 4, "상품4")), "주문4", 12000);

        jpaOrderRepository.saveAll(List.of(order1, order2, order3, order4));

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("특정 업체 주문 조회")
    void findAllCompanyId_whenGivenCompanyId() {
        // when
        Page<Order> foundOrders = jpaOrderRepository.findAllByCompanyId(companyId, PageRequest.of(0, 10));

        // then
        assertThat(foundOrders).hasSize(4);
        assertThat(foundOrders).extracting("requestMessage")
                .containsExactlyInAnyOrder("주문1", "주문2", "주문3", "주문4");
        assertThat(foundOrders).extracting("totalPrice")
                .containsExactlyInAnyOrder(2000, 15000, 1000, 12000);
    }

    @Test
    @DisplayName("특정 업체 주문 조회 - 총 가격 내림차순 정렬")
    void findAllCompanyId_whenGivenCompanyIdAndSortByTotalPriceDesc() {
        // when
        Page<Order> foundOrders = jpaOrderRepository.findAllByCompanyId(companyId, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "totalPrice")));

        // then
        assertThat(foundOrders).hasSize(4);
        assertThat(foundOrders.getContent().get(0).getRequestMessage())
                .isEqualTo("주문2");
        assertThat(foundOrders.getContent().get(0).getTotalPrice())
                .isEqualTo(15000);
    }

    @Test
    @DisplayName("특정 업체 주문 조회 - 페이징")
    void findAllByCompanyId_whenGivenCompanyIdAndPageable() {
        // when
        Page<Order> foundOrders = jpaOrderRepository.findAllByCompanyId(companyId, PageRequest.of(0, 2));

        // then
        assertThat(foundOrders).hasSize(2);
    }

    @Test
    @DisplayName("특정 업체 주문 조회 - 정렬 + 페이징")
    void findAllByCompanyId_whenGivenCompanyIdAndPageableWithSort() {
        // when
        Page<Order> foundOrders = jpaOrderRepository.findAllByCompanyId(companyId, PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "totalPrice")));

        // then
        assertThat(foundOrders).hasSize(2);
        assertThat(foundOrders.getContent().get(0).getTotalPrice()).isEqualTo(15000);
        assertThat(foundOrders.getContent().get(0).getRequestMessage()).isEqualTo("주문2");
        assertThat(foundOrders.getContent().get(1).getTotalPrice()).isEqualTo(12000);
        assertThat(foundOrders.getContent().get(1).getRequestMessage()).isEqualTo("주문4");
    }

    @Test
    @DisplayName("존재하지 않는 주문 조회")
    void findById_whenOrderNotFound() {
        // when
        Optional<Order> findOrder = jpaOrderRepository.findById(UUID.randomUUID());

        // then
        assertThat(findOrder.isPresent()).isEqualTo(false);
    }

    @Test
    @DisplayName("주문 상품 조회")
    void findById_whenFindingById() {
        // given
        Order order = getOrder(companyId, getOrderCompanies(getOrderProducts(product1Id, 1000, 2, "상품1")), "주문1", 2000);
        Order savedOrder = jpaOrderRepository.save(order);
        em.flush();
        em.clear();

        // when
        Optional<Order> findOrder = jpaOrderRepository.findById(savedOrder.getId());

        // then
        assertThat(findOrder.get().getVendorOrders().size()).isEqualTo(1);
        assertThat(findOrder.get().getVendorOrders().get(0).getOrderProducts().get(0).getProductId()).isEqualTo(product1Id);
        assertThat(findOrder.get().getVendorOrders().get(0).getOrderProducts().get(0).getName()).isEqualTo("상품1");
        assertThat(findOrder.get().getVendorOrders().get(0).getOrderProducts().get(0).getQuantity()).isEqualTo(2);
        assertThat(findOrder.get().getVendorOrders().get(0).getOrderProducts().get(0).getPrice()).isEqualTo(1000);
    }


    // 테스트 헬퍼 메서드
    private Order getOrder(UUID companyId, List<VendorOrder> orderCompanies, String requestMessage, int totalPrice) {
        return Order.create(orderCompanies, 1L, companyId, requestMessage, totalPrice, "멱등키");
    }

    private List<VendorOrder> getOrderCompanies(List<OrderProduct> products) {
        return List.of(VendorOrder.create(UUID.randomUUID(), products));
    }

    private List<OrderProduct> getOrderProducts(UUID productId,int price, int quantity, String name) {
        return List.of(OrderProduct.create(productId, price, quantity, name));
    }
}
