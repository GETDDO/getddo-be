package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketHistoryTest {

	private static final Instant ISSUED_AT = Instant.parse("2026-09-15T03:00:00Z");
	private static final Instant EXPIRES_AT = Instant.parse("2026-09-30T15:00:00Z");

	private static Ticket saved() {
		return new Ticket(UUID.randomUUID(), UUID.randomUUID(),
				new GrantSource(GrantSourceType.MISSION, UUID.randomUUID()), TicketGrade.GOLD, TicketStatus.AVAILABLE,
				EXPIRES_AT, 1, ISSUED_AT, ISSUED_AT);
	}

	@Test
	@DisplayName("지급 이력은 저장된 응모권의 지급 당시 상태·만료 시각·시각과 같다")
	void grantHistoryMirrorsIssuedTicket() {
		// given
		Ticket ticket = saved();
		// when
		TicketHistory history = TicketHistory.grant(ticket, "미션 완료");
		// then
		assertThat(history.getId()).isNull();
		assertThat(history.getTicketId()).isEqualTo(ticket.getId());
		assertThat(history.getOperationType()).isEqualTo(TicketOperationType.GRANT);
		assertThat(history.getTicketVersion()).isEqualTo(1);
		assertThat(history.getStatus()).isEqualTo(TicketStatus.AVAILABLE);
		assertThat(history.getExpiresAt()).isEqualTo(EXPIRES_AT);
		assertThat(history.getCreatedAt()).isEqualTo(ISSUED_AT);
		assertThat(history.getReason()).isEqualTo("미션 완료");
		assertThat(history.getEventEntryId()).isNull();
		assertThat(history.getOriginalUseHistoryId()).isNull();
		assertThat(history.getCorrectedHistoryId()).isNull();
	}

	@Test
	@DisplayName("저장되지 않은 응모권으로는 지급 이력을 만들 수 없다")
	void requiresSavedTicket() {
		// given
		Ticket unsaved = Ticket.issue(UUID.randomUUID(), new GrantSource(GrantSourceType.GAME, UUID.randomUUID()),
				TicketGrade.BRONZE, EXPIRES_AT, ISSUED_AT);
		// when
		// then
		assertThatThrownBy(() -> TicketHistory.grant(unsaved, "사유")).isInstanceOf(NullPointerException.class);
	}
}
