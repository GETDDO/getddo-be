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

	@Test
	@DisplayName("사용 이력은 사용된 응모권의 상태·만료 시각·수정 시각과 응모 ID를 담는다")
	void useHistoryMirrorsSpentTicket() {
		// given
		Instant usedAt = ISSUED_AT.plusSeconds(60);
		Ticket spent = saved().use(usedAt);
		UUID entryId = UUID.randomUUID();
		// when
		TicketHistory history = TicketHistory.use(spent, entryId, "이벤트 응모");
		// then
		assertThat(history.getOperationType()).isEqualTo(TicketOperationType.USE);
		assertThat(history.getTicketVersion()).isEqualTo(2);
		assertThat(history.getStatus()).isEqualTo(TicketStatus.SPENT);
		assertThat(history.getExpiresAt()).isEqualTo(EXPIRES_AT);
		assertThat(history.getCreatedAt()).isEqualTo(usedAt);
		assertThat(history.getEventEntryId()).isEqualTo(entryId);
		assertThat(history.getOriginalUseHistoryId()).isNull();
	}

	@Test
	@DisplayName("반환 이력은 반환된 응모권의 새 만료 시각과 원본 사용 이력 ID를 담는다")
	void refundHistoryLinksOriginalUse() {
		// given
		Instant refundedAt = ISSUED_AT.plusSeconds(120);
		Instant newExpiresAt = Instant.parse("2026-10-31T15:00:00Z");
		Ticket returned = saved().use(ISSUED_AT.plusSeconds(60)).refund(refundedAt, newExpiresAt);
		UUID useHistoryId = UUID.randomUUID();
		// when
		TicketHistory history = TicketHistory.refund(returned, useHistoryId, "이벤트 취소");
		// then
		assertThat(history.getOperationType()).isEqualTo(TicketOperationType.REFUND);
		assertThat(history.getTicketVersion()).isEqualTo(3);
		assertThat(history.getStatus()).isEqualTo(TicketStatus.RETURNED);
		assertThat(history.getExpiresAt()).isEqualTo(newExpiresAt);
		assertThat(history.getCreatedAt()).isEqualTo(refundedAt);
		assertThat(history.getOriginalUseHistoryId()).isEqualTo(useHistoryId);
		assertThat(history.getEventEntryId()).isNull();
	}

	@Test
	@DisplayName("처리 유형·상태·버전·연결 ID가 DB 제약과 어긋난 이력은 만들 수 없다")
	void rejectsInconsistentCombinations() {
		// given
		UUID ticketId = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		// when
		// then
		assertThatThrownBy(() -> new TicketHistory(null, ticketId, TicketOperationType.USE, 2, TicketStatus.SPENT,
				EXPIRES_AT, null, null, null, "사유", ISSUED_AT)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TicketHistory(null, ticketId, TicketOperationType.REFUND, 3,
				TicketStatus.RETURNED, EXPIRES_AT, null, null, null, "사유", ISSUED_AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TicketHistory(null, ticketId, TicketOperationType.GRANT, 2,
				TicketStatus.AVAILABLE, EXPIRES_AT, null, null, null, "사유", ISSUED_AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TicketHistory(null, ticketId, TicketOperationType.USE, 1, TicketStatus.SPENT,
				EXPIRES_AT, other, null, null, "사유", ISSUED_AT)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TicketHistory(null, ticketId, TicketOperationType.USE, 2,
				TicketStatus.RETURNED, EXPIRES_AT, other, null, null, "사유", ISSUED_AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TicketHistory(null, ticketId, TicketOperationType.EXPIRE, 2,
				TicketStatus.EXPIRED, EXPIRES_AT, other, null, null, "사유", ISSUED_AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(new TicketHistory(null, ticketId, TicketOperationType.EXPIRE, 2, TicketStatus.EXPIRED, EXPIRES_AT,
				null, null, null, "사유", ISSUED_AT).getStatus()).isEqualTo(TicketStatus.EXPIRED);
	}

	@Test
	@DisplayName("만료 이력은 만료된 응모권의 상태와 버전을 담는다")
	void expireHistoryMirrorsExpiredTicket() {
		// given
		Ticket base = saved();
		Ticket expired = new Ticket(base.getId(), base.getUserId(), base.getGrantSource(), base.getGrade(),
				TicketStatus.EXPIRED, EXPIRES_AT, 2, ISSUED_AT, EXPIRES_AT);
		// when
		TicketHistory history = TicketHistory.expire(expired, "월말 만료");
		// then
		assertThat(history.getOperationType()).isEqualTo(TicketOperationType.EXPIRE);
		assertThat(history.getStatus()).isEqualTo(TicketStatus.EXPIRED);
		assertThat(history.getTicketVersion()).isEqualTo(2);
		assertThat(history.getCreatedAt()).isEqualTo(EXPIRES_AT);
	}

	@Test
	@DisplayName("정정 대상 이력 ID는 정정 이력에만 있어야 한다")
	void correctedHistoryIdOnlyOnCorrection() {
		// given
		UUID ticketId = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		// when
		// then
		assertThatThrownBy(() -> new TicketHistory(null, ticketId, TicketOperationType.USE, 2, TicketStatus.SPENT,
				EXPIRES_AT, other, null, other, "사유", ISSUED_AT)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TicketHistory(null, ticketId, TicketOperationType.CORRECTION, 2,
				TicketStatus.AVAILABLE, EXPIRES_AT, null, null, null, "사유", ISSUED_AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(new TicketHistory(null, ticketId, TicketOperationType.CORRECTION, 2, TicketStatus.AVAILABLE,
				EXPIRES_AT, null, null, other, "사유", ISSUED_AT).getCorrectedHistoryId()).isEqualTo(other);
	}
}
