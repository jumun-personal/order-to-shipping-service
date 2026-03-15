package com.jumunhasyeotjo.order_to_shipping.shipping.infrastructure.repository;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jumunhasyeotjo.order_to_shipping.shipping.domain.entity.Shipping;
import com.jumunhasyeotjo.order_to_shipping.shipping.domain.entity.ShippingHistory;
import com.jumunhasyeotjo.order_to_shipping.shipping.domain.vo.RouteInfo;
import com.jumunhasyeotjo.order_to_shipping.shipping.fixtures.ShippingFixture;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(ShippingHistoryRepositoryAdapter.class)
class ShippingHistoryRepositoryTest {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

	@Autowired
	private ShippingHistoryRepositoryAdapter shippingHistoryRepositoryAdapter;

	@Autowired
	private JpaShippingHistoryRepository jpaShippingHistoryRepository;

	@PersistenceContext
	private EntityManager em;

	@Test
	@DisplayName("saveAll: 배송이력을 모두 저장하고 반환한다")
	void saveAll_shouldPersistAndReturnEntities() {
		// given
		Shipping shipping = persistShipping();

		ShippingHistory h1 = ShippingHistory.create(
			shipping,
			1L,
			1,
			"서울허브",
			"대전허브",
			RouteInfo.of(120, 90)
		);

		ShippingHistory h2 = ShippingHistory.create(
			shipping,
			2L,
			2,
			"대전허브",
			"부산허브",
			RouteInfo.of(300, 240)
		);

		// when
		List<ShippingHistory> saved = shippingHistoryRepositoryAdapter.saveAll(List.of(h1, h2));

		// then
		assertThat(saved).hasSize(2);

		em.flush();
		em.clear();

		List<ShippingHistory> found = shippingHistoryRepositoryAdapter.findAllByShippingId(shipping.getId());
		assertThat(found).hasSize(2);
		assertThat(found)
			.extracting(ShippingHistory::getSequence)
			.containsExactlyInAnyOrder(1, 2);
		assertThat(found)
			.extracting(ShippingHistory::getOrigin, ShippingHistory::getDestination)
			.containsExactlyInAnyOrder(
				org.assertj.core.groups.Tuple.tuple("서울허브", "대전허브"),
				org.assertj.core.groups.Tuple.tuple("대전허브", "부산허브")
			);
	}

	@Test
	@DisplayName("findAllByShippingId 실행시 다른 배송의 이력은 조회되지 않는다")
	void findAllByShippingId_shouldReturnOnlyTargetShippingHistories() {
		// given
		Shipping shipping1 = persistShipping();
		Shipping shipping2 = persistShipping();

		ShippingHistory s1h1 = ShippingHistory.create(
			shipping1, 1L, 1, "A", "B", RouteInfo.of(10, 10)
		);
		ShippingHistory s1h2 = ShippingHistory.create(
			shipping1, 2L, 2, "B", "C", RouteInfo.of(20, 20)
		);
		ShippingHistory s2h1 = ShippingHistory.create(
			shipping2, 3L, 1, "X", "Y", RouteInfo.of(30, 30)
		);

		jpaShippingHistoryRepository.saveAll(List.of(s1h1, s1h2, s2h1));
		em.flush();
		em.clear();

		// when
		List<ShippingHistory> found = shippingHistoryRepositoryAdapter.findAllByShippingId(shipping1.getId());

		// then
		assertThat(found).hasSize(2);
		assertThat(found)
			.extracting(ShippingHistory::getOrigin)
			.containsExactlyInAnyOrder("A", "B");
	}

	private Shipping persistShipping() {
		Shipping shipping = ShippingFixture.createDefault();
		em.persist(shipping);
		return shipping;
	}
}
