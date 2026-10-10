package com.getddo.core.entry.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.event.domain.MembershipRule;
import com.getddo.core.user.domain.Membership;

import static org.assertj.core.api.Assertions.assertThat;

class EntryEventTest {

	private static final Instant STARTS_AT = Instant.parse("2026-09-15T00:00:00Z");
	private static final Instant ENDS_AT = Instant.parse("2026-09-16T00:00:00Z");

	private static EntryEvent event(EventType type, boolean weighted, Integer max, MembershipRule rule,
			EventStatus status, boolean deleted) {
		return new EntryEvent(UUID.randomUUID(), "이벤트", type, weighted, max, rule, STARTS_AT, ENDS_AT, status,
				deleted);
	}

	private static EntryEvent open() {
		return event(EventType.TICKET, true, 5, MembershipRule.excellent, EventStatus.OPEN, false);
	}

	@Test
	@DisplayName("모집 시작 시각 정각부터 마감 시각 직전까지 응모를 받고 마감 시각 정각부터는 받지 않는다")
	void opensAtStartAndClosesExactlyAtEnd() {
		// given
		EntryEvent event = open();
		// when
		// then
		assertThat(event.isOpenAt(STARTS_AT.minusNanos(1_000))).isFalse();
		assertThat(event.isOpenAt(STARTS_AT)).isTrue();
		assertThat(event.isOpenAt(ENDS_AT.minusNanos(1_000))).isTrue();
		assertThat(event.isOpenAt(ENDS_AT)).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = EventStatus.class, names = {"SCHEDULED", "OPEN"})
	@DisplayName("모집 중인 상태는 시간이 맞으면 응모를 받는다")
	void acceptingStatusesOpenByTime(EventStatus status) {
		// given
		EntryEvent event = event(EventType.TICKET, true, 5, MembershipRule.excellent, status, false);
		// when
		// then
		assertThat(event.isOpenAt(STARTS_AT.plusSeconds(60))).isTrue();
	}

	@ParameterizedTest
	@EnumSource(value = EventStatus.class, names = {"SCHEDULED", "OPEN"}, mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("마감·취소·추첨 이후 상태는 시간이 맞아도 응모를 받지 않는다")
	void closedStatusesNeverOpen(EventStatus status) {
		// given
		EntryEvent event = event(EventType.TICKET, true, 5, MembershipRule.excellent, status, false);
		// when
		// then
		assertThat(event.isOpenAt(STARTS_AT.plusSeconds(60))).isFalse();
	}

	@Test
	@DisplayName("삭제된 이벤트는 모집 시간 안이어도 응모를 받지 않는다")
	void deletedEventIsNeverOpen() {
		// given
		EntryEvent event = event(EventType.TICKET, true, 5, MembershipRule.excellent, EventStatus.OPEN, true);
		// when
		// then
		assertThat(event.isOpenAt(STARTS_AT.plusSeconds(60))).isFalse();
	}

	@Test
	@DisplayName("멤버십은 우수 < VIP < VVIP 순서이며 최소 허용 등급 이상이면 허용하고 멤버십이 없으면 허용하지 않는다")
	void membershipOrdering() {
		// given
		EntryEvent excellent = event(EventType.TICKET, true, 5, MembershipRule.excellent, EventStatus.OPEN, false);
		EntryEvent vip = event(EventType.TICKET, true, 5, MembershipRule.vip, EventStatus.OPEN, false);
		EntryEvent vvip = event(EventType.TICKET, true, 5, MembershipRule.vvip, EventStatus.OPEN, false);
		// when
		// then
		assertThat(excellent.allows(Membership.EXCELLENT)).isTrue();
		assertThat(vip.allows(Membership.EXCELLENT)).isFalse();
		assertThat(vip.allows(Membership.VIP)).isTrue();
		assertThat(vip.allows(Membership.VVIP)).isTrue();
		assertThat(vvip.allows(Membership.VIP)).isFalse();
		assertThat(vvip.allows(Membership.VVIP)).isTrue();
		assertThat(excellent.allows(null)).isFalse();
	}

	@Test
	@DisplayName("사용 상한은 응모권 미사용 0, 가중치 미적용 1, 일반 가중치는 등록한 값, 월말 소진용은 null이다")
	void ticketLimitByEventKind() {
		// given
		EntryEvent noTicket = event(EventType.NO_TICKET, false, null, MembershipRule.excellent, EventStatus.OPEN, false);
		EntryEvent unweighted = event(EventType.TICKET, false, 1, MembershipRule.excellent, EventStatus.OPEN, false);
		EntryEvent weighted = event(EventType.TICKET, true, 5, MembershipRule.excellent, EventStatus.OPEN, false);
		EntryEvent monthEnd = event(EventType.TICKET, true, null, MembershipRule.excellent, EventStatus.OPEN, false);
		// when
		// then
		assertThat(noTicket.ticketLimit()).isZero();
		assertThat(noTicket.usesTickets()).isFalse();
		assertThat(unweighted.ticketLimit()).isEqualTo(1L);
		assertThat(unweighted.isUnweighted()).isTrue();
		assertThat(weighted.ticketLimit()).isEqualTo(5L);
		assertThat(weighted.isUnweighted()).isFalse();
		assertThat(monthEnd.ticketLimit()).isNull();
	}
}
