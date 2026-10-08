package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MyTicketsTest {

	private static final Instant NOW = Instant.parse("2026-09-15T03:00:00Z");
	private static final Instant EXPIRES_AT = Instant.parse("2026-09-30T15:00:00Z");

	@Test
	@DisplayName("묶음의 장수를 더해 합계와 등급별 장수를 구하고 등급이 달라도 1장은 1로 센다")
	void sumsHoldings() {
		// given
		List<TicketHolding> holdings = List.of(
				new TicketHolding(TicketGrade.BRONZE, EXPIRES_AT, 4),
				new TicketHolding(TicketGrade.BRONZE, EXPIRES_AT.plusSeconds(86400), 2),
				new TicketHolding(TicketGrade.GOLD, EXPIRES_AT, 1));
		// when
		MyTickets result = MyTickets.of(holdings, NOW);
		// then
		assertThat(result.getAvailableCount()).isEqualTo(7);
		assertThat(result.getCountByGrade()).containsEntry(TicketGrade.BRONZE, 6L)
				.containsEntry(TicketGrade.SILVER, 0L).containsEntry(TicketGrade.GOLD, 1L);
		assertThat(result.getHoldings()).isEqualTo(holdings);
		assertThat(result.getServerTime()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("보유가 없어도 모든 등급을 0장으로 포함한다")
	void includesEveryGradeWhenEmpty() {
		// given
		// when
		MyTickets result = MyTickets.of(List.of(), NOW);
		// then
		assertThat(result.getAvailableCount()).isZero();
		assertThat(result.getCountByGrade()).containsOnlyKeys(TicketGrade.values());
		assertThat(result.getCountByGrade().values()).containsOnly(0L);
	}

	@Test
	@DisplayName("보유 묶음은 등급·만료 시각이 없거나 장수가 1 미만이면 만들 수 없다")
	void holdingRejectsInvalidValues() {
		// given
		// when
		// then
		assertThatThrownBy(() -> new TicketHolding(null, EXPIRES_AT, 1)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new TicketHolding(TicketGrade.BRONZE, null, 1))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new TicketHolding(TicketGrade.BRONZE, EXPIRES_AT, 0))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TicketHolding(TicketGrade.BRONZE, EXPIRES_AT, -1))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
