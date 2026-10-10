package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final Instant ISSUED_AT = Instant.parse("2026-09-15T03:00:00Z");
	private static final Instant EXPIRES_AT = Instant.parse("2026-09-30T15:00:00Z");

	@Test
	@DisplayName("새 응모권은 사용 가능 상태, 버전 1, 생성·수정 시각이 지급 시각과 같다")
	void issuesAvailableTicket() {
		// given
		GrantSource source = new GrantSource(GrantSourceType.MISSION, UUID.randomUUID());
		// when
		Ticket ticket = Ticket.issue(USER_ID, source, TicketGrade.SILVER, EXPIRES_AT, ISSUED_AT);
		// then
		assertThat(ticket.getId()).isNull();
		assertThat(ticket.getUserId()).isEqualTo(USER_ID);
		assertThat(ticket.getGrantSource()).isEqualTo(source);
		assertThat(ticket.getGrade()).isEqualTo(TicketGrade.SILVER);
		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.AVAILABLE);
		assertThat(ticket.getVersion()).isEqualTo(1);
		assertThat(ticket.getExpiresAt()).isEqualTo(EXPIRES_AT);
		assertThat(ticket.getCreatedAt()).isEqualTo(ISSUED_AT);
		assertThat(ticket.getUpdatedAt()).isEqualTo(ISSUED_AT);
	}

	@Test
	@DisplayName("출석 보상에 브론즈가 아닌 등급을 주면 거절한다")
	void rejectsNonBronzeAttendance() {
		// given
		GrantSource attendance = new GrantSource(GrantSourceType.ATTENDANCE, UUID.randomUUID());
		// when
		// then
		assertThatThrownBy(() -> Ticket.issue(USER_ID, attendance, TicketGrade.SILVER, EXPIRES_AT, ISSUED_AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(Ticket.issue(USER_ID, attendance, TicketGrade.BRONZE, EXPIRES_AT, ISSUED_AT).getGrade())
				.isEqualTo(TicketGrade.BRONZE);
	}

	@Test
	@DisplayName("필수 값이 없으면 거절한다")
	void rejectsMissingValues() {
		// given
		GrantSource source = new GrantSource(GrantSourceType.GAME, UUID.randomUUID());
		// when
		// then
		assertThatThrownBy(() -> Ticket.issue(null, source, TicketGrade.BRONZE, EXPIRES_AT, ISSUED_AT))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Ticket.issue(USER_ID, null, TicketGrade.BRONZE, EXPIRES_AT, ISSUED_AT))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Ticket.issue(USER_ID, source, null, EXPIRES_AT, ISSUED_AT))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Ticket.issue(USER_ID, source, TicketGrade.BRONZE, null, ISSUED_AT))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Ticket.issue(USER_ID, source, TicketGrade.BRONZE, EXPIRES_AT, null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("복원 경로도 같은 불변식을 검증해 출석 비브론즈·버전 0·시각 역전·필수 값 누락을 거절한다")
	void constructorEnforcesInvariants() {
		// given
		GrantSource attendance = new GrantSource(GrantSourceType.ATTENDANCE, UUID.randomUUID());
		GrantSource mission = new GrantSource(GrantSourceType.MISSION, UUID.randomUUID());
		// when
		// then
		assertThatThrownBy(() -> new Ticket(UUID.randomUUID(), USER_ID, attendance, TicketGrade.GOLD,
				TicketStatus.AVAILABLE, EXPIRES_AT, 1, ISSUED_AT, ISSUED_AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Ticket(UUID.randomUUID(), USER_ID, mission, TicketGrade.GOLD,
				TicketStatus.AVAILABLE, EXPIRES_AT, 0, ISSUED_AT, ISSUED_AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Ticket(UUID.randomUUID(), USER_ID, mission, TicketGrade.GOLD,
				TicketStatus.AVAILABLE, EXPIRES_AT, 1, ISSUED_AT, ISSUED_AT.minusSeconds(1)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Ticket(UUID.randomUUID(), USER_ID, mission, TicketGrade.GOLD,
				null, EXPIRES_AT, 1, ISSUED_AT, ISSUED_AT))
				.isInstanceOf(NullPointerException.class);
		assertThat(new Ticket(UUID.randomUUID(), USER_ID, mission, TicketGrade.GOLD, TicketStatus.SPENT,
				EXPIRES_AT, 3, ISSUED_AT, ISSUED_AT.plusSeconds(5)).getVersion()).isEqualTo(3);
	}

	private static Ticket ticketWith(TicketStatus status, Instant expiresAt, long version) {
		return new Ticket(UUID.randomUUID(), USER_ID, new GrantSource(GrantSourceType.MISSION, UUID.randomUUID()),
				TicketGrade.SILVER, status, expiresAt, version, ISSUED_AT, ISSUED_AT);
	}

	@Test
	@DisplayName("사용하면 사용됨·버전 +1·수정 시각이 사용 시각이 되고 만료 시각과 등급은 그대로이며 원본은 바뀌지 않는다")
	void useSpendsTicket() {
		// given
		Ticket available = ticketWith(TicketStatus.AVAILABLE, EXPIRES_AT, 1);
		Ticket returned = ticketWith(TicketStatus.RETURNED, EXPIRES_AT, 3);
		Instant usedAt = ISSUED_AT.plusSeconds(60);
		// when
		Ticket spent = available.use(usedAt);
		Ticket spentAgain = returned.use(usedAt);
		// then
		assertThat(spent.getId()).isEqualTo(available.getId());
		assertThat(spent.getStatus()).isEqualTo(TicketStatus.SPENT);
		assertThat(spent.getVersion()).isEqualTo(2);
		assertThat(spent.getUpdatedAt()).isEqualTo(usedAt);
		assertThat(spent.getCreatedAt()).isEqualTo(ISSUED_AT);
		assertThat(spent.getExpiresAt()).isEqualTo(EXPIRES_AT);
		assertThat(spent.getGrade()).isEqualTo(TicketGrade.SILVER);
		assertThat(spentAgain.getVersion()).isEqualTo(4);
		assertThat(available.getStatus()).isEqualTo(TicketStatus.AVAILABLE);
		assertThat(available.getVersion()).isEqualTo(1);
	}

	@Test
	@DisplayName("이미 사용·만료된 응모권이나 만료 시각이 지난 응모권은 사용할 수 없다")
	void useRejectsUnusableTicket() {
		// given
		Instant usedAt = EXPIRES_AT;
		// when
		// then
		assertThatThrownBy(() -> ticketWith(TicketStatus.SPENT, EXPIRES_AT.plusSeconds(1), 2).use(usedAt))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> ticketWith(TicketStatus.EXPIRED, EXPIRES_AT.plusSeconds(1), 2).use(usedAt))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> ticketWith(TicketStatus.AVAILABLE, EXPIRES_AT, 1).use(usedAt))
				.isInstanceOf(IllegalStateException.class);
		assertThat(ticketWith(TicketStatus.AVAILABLE, EXPIRES_AT, 1).use(usedAt.minusNanos(1000)).getStatus())
				.isEqualTo(TicketStatus.SPENT);
	}

	@Test
	@DisplayName("반환하면 반환됨·버전 +1·새 만료 시각이 되고 등급은 그대로다")
	void refundReturnsTicket() {
		// given
		Ticket spent = ticketWith(TicketStatus.SPENT, EXPIRES_AT, 2);
		Instant refundedAt = ISSUED_AT.plusSeconds(120);
		Instant newExpiresAt = Instant.parse("2026-10-31T15:00:00Z");
		// when
		Ticket returned = spent.refund(refundedAt, newExpiresAt);
		// then
		assertThat(returned.getStatus()).isEqualTo(TicketStatus.RETURNED);
		assertThat(returned.getVersion()).isEqualTo(3);
		assertThat(returned.getExpiresAt()).isEqualTo(newExpiresAt);
		assertThat(returned.getUpdatedAt()).isEqualTo(refundedAt);
		assertThat(returned.getGrade()).isEqualTo(TicketGrade.SILVER);
		assertThat(returned.getStatus().isUsable()).isTrue();
		assertThat(spent.getStatus()).isEqualTo(TicketStatus.SPENT);
	}

	@Test
	@DisplayName("사용됨이 아닌 응모권은 반환할 수 없고 새 만료 시각은 반환 시각보다 늦어야 한다")
	void refundRejectsInvalidInput() {
		// given
		Instant refundedAt = ISSUED_AT.plusSeconds(120);
		// when
		// then
		assertThatThrownBy(() -> ticketWith(TicketStatus.AVAILABLE, EXPIRES_AT, 1).refund(refundedAt, EXPIRES_AT))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> ticketWith(TicketStatus.RETURNED, EXPIRES_AT, 3).refund(refundedAt, EXPIRES_AT))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> ticketWith(TicketStatus.SPENT, EXPIRES_AT, 2).refund(refundedAt, refundedAt))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
