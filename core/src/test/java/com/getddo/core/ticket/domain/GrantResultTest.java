package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrantResultTest {

	private static final Instant GRANTED_AT = Instant.parse("2026-09-15T03:00:00Z");
	private static final Instant EXPIRES_AT = Instant.parse("2026-09-30T15:00:00Z");

	@Test
	@DisplayName("같은 지급 건의 응모권으로 재요청 결과를 만들면 장수와 지급 당시 값을 replayed=true로 돌려준다")
	void replaysUniformGrant() {
		// given
		List<GrantedTicket> granted = List.of(granted(TicketGrade.SILVER, EXPIRES_AT),
				granted(TicketGrade.SILVER, EXPIRES_AT));
		// when
		GrantResult result = GrantResult.replayOf(granted);
		// then
		assertThat(result).usingRecursiveComparison()
				.isEqualTo(new GrantResult(2, TicketGrade.SILVER, GRANTED_AT, EXPIRES_AT, true));
	}

	@Test
	@DisplayName("빈 목록으로 재요청 결과를 만들 수 없다")
	void rejectsEmpty() {
		// given
		// when
		// then
		assertThatThrownBy(() -> GrantResult.replayOf(List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> GrantResult.replayOf(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("응모권끼리 등급이나 만료 시각이 다르면 한 지급 건이 아니므로 거절한다")
	void rejectsMixedGrant() {
		// given
		List<GrantedTicket> differentGrade = List.of(granted(TicketGrade.BRONZE, EXPIRES_AT),
				granted(TicketGrade.GOLD, EXPIRES_AT));
		List<GrantedTicket> differentExpiry = List.of(granted(TicketGrade.BRONZE, EXPIRES_AT),
				granted(TicketGrade.BRONZE, EXPIRES_AT.plusSeconds(1)));
		// when
		// then
		assertThatThrownBy(() -> GrantResult.replayOf(differentGrade)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> GrantResult.replayOf(differentExpiry)).isInstanceOf(IllegalStateException.class);
	}

	private static GrantedTicket granted(TicketGrade grade, Instant expiresAt) {
		return new GrantedTicket(UUID.randomUUID(), grade, GRANTED_AT, expiresAt);
	}
}
