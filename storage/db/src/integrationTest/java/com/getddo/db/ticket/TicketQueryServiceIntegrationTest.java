package com.getddo.db.ticket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.common.pagination.CursorQuery;
import com.getddo.core.common.pagination.CursorResult;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.MyTickets;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketHistoryFilter;
import com.getddo.core.ticket.domain.TicketHistoryView;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.ticket.service.TicketQueryService;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** GD-46 테스트 방법(지급 후 보유 장수, 만료분 제외, 커서 이동 안정성)을 실제 MySQL에서 검증한다. */
class TicketQueryServiceIntegrationTest extends TicketIntegrationTestSupport {

	private static final Instant SEPTEMBER = Instant.parse("2026-09-15T03:00:00Z");
	/** 10월 1일 00:30 KST. 9월 지급분이 막 만료된 시각이다. */
	private static final Instant OCTOBER_FIRST = Instant.parse("2026-09-30T15:30:00Z");

	@Autowired
	private TicketQueryService queryService;

	@Test
	@DisplayName("지급 후 보유 장수가 지급한 장수 합계와 같고 등급별 장수의 합과 같다")
	void countMatchesGrants() {
		// given
		clock.set(SEPTEMBER);
		TicketGrantSeeds.AttendanceParents attendance = seeds.attendanceParents(userId);
		transaction.executeWithoutResult(status -> {
			UUID claim = seeds.attendanceClaim(userId, attendance, 3);
			grantService.grant(command(userId, GrantSourceType.ATTENDANCE, claim, 3));
		});
		grantNewMissionClaim(userId, 1);
		// when
		MyTickets result = queryService.getMyTickets(userId);
		// then
		assertThat(result.getAvailableCount()).isEqualTo(4)
				.isEqualTo(count("select count(*) from tickets where user_id = ?", bytes(userId)));
		assertThat(result.getCountByGrade().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(4);
		assertThat(result.getCountByGrade().get(TicketGrade.BRONZE)).isGreaterThanOrEqualTo(3);
		assertThat(result.getServerTime()).isEqualTo(SEPTEMBER);
	}

	@Test
	@DisplayName("만료 처리 전이라도 만료 시각이 지난 응모권과 만료 처리된 응모권은 보유 장수에서 빠진다")
	void excludesExpiredTickets() {
		// given: 8월분은 만료 처리됨, 9월분은 만료 시각이 지났지만 아직 AVAILABLE로 저장됨
		insertTicket(userId, missionClaimOf(userId), TicketGrade.BRONZE, TicketStatus.EXPIRED,
				"2026-08-31T15:00:00Z");
		clock.set(SEPTEMBER);
		grantNewMissionClaim(userId, 1);
		clock.set(OCTOBER_FIRST);
		grantNewMissionClaim(userId, 1);
		// when
		MyTickets result = queryService.getMyTickets(userId);
		// then
		assertThat(result.getAvailableCount()).isEqualTo(1);
		assertThat(result.getServerTime()).isEqualTo(OCTOBER_FIRST);
		assertThat(result.getHoldings()).singleElement().satisfies(holding ->
				assertThat(holding.getExpiresAt()).isEqualTo(Instant.parse("2026-10-31T15:00:00Z")));
		assertThat(jdbc.queryForObject("""
				select count(*) from tickets where user_id = ? and status = 'AVAILABLE'
				""", Long.class, bytes(userId))).isEqualTo(2L);
	}

	@Test
	@DisplayName("다음 커서를 따라가면 전체 수만큼 중복 없이 최신순으로 받고 마지막에 커서가 없다")
	void followsNextCursorToTheEnd() {
		// given
		clock.set(SEPTEMBER);
		for (int i = 0; i < 5; i++) {
			grantNewMissionClaim(userId, 1);
		}
		// when
		List<TicketHistoryView> received = new ArrayList<>();
		List<Long> totals = new ArrayList<>();
		String cursor = null;
		int pages = 0;
		do {
			CursorResult<TicketHistoryView> page =
					queryService.getMyHistory(userId, TicketHistoryFilter.none(), new CursorQuery(cursor, 2));
			received.addAll(page.getItems());
			totals.add(page.getTotalElements());
			cursor = page.getNextCursor();
			pages++;
		} while (cursor != null && pages < 10);
		// then
		assertThat(pages).isEqualTo(3);
		assertThat(totals).containsOnly(5L);
		assertThat(received).hasSize(5).extracting(TicketHistoryView::getId).doesNotHaveDuplicates();
		assertThat(received).extracting(TicketHistoryView::getCreatedAt)
				.isSortedAccordingTo((a, b) -> b.compareTo(a));
	}

	@Test
	@DisplayName("해석할 수 없는 커서는 TICKET-004로 거절한다")
	void rejectsMalformedCursor() {
		// given
		CursorQuery page = new CursorQuery("broken", 20);
		// when
		// then
		assertThatThrownBy(() -> queryService.getMyHistory(userId, TicketHistoryFilter.none(), page))
				.isInstanceOf(TicketException.class)
				.extracting(error -> ((TicketException) error).getErrorCode())
				.isEqualTo(TicketErrorCode.TICKET_INVALID_HISTORY_QUERY);
	}

	private UUID missionClaimOf(UUID user) {
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(user);
		return transaction.execute(status -> seeds.missionClaim(user, parents, 1));
	}
}
