package com.getddo.db.ticket;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketHistoryCursor;
import com.getddo.core.ticket.domain.TicketHistoryFilter;
import com.getddo.core.ticket.domain.TicketHistoryView;
import com.getddo.core.ticket.domain.TicketHolding;
import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.ticket.repository.TicketQueryRepository;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;

/** 화면용 응모권 조회 SQL을 Flyway로 만든 실제 MySQL 스키마에서 검증한다. */
class TicketQueryRepositoryIntegrationTest extends TicketIntegrationTestSupport {

	private static final Instant SEPTEMBER = Instant.parse("2026-09-15T03:00:00Z");

	@Autowired
	private TicketQueryRepository queryRepository;

	@Test
	@DisplayName("사용 가능·반환 상태이고 만료 전인 응모권만 등급·만료 시각별로 묶어 만료 임박순으로 센다")
	void groupsAvailableTicketsByGradeAndExpiry() {
		// given
		UUID claim = missionClaimOf(userId);
		insertTicket(userId, claim, TicketGrade.BRONZE, TicketStatus.AVAILABLE, "2026-10-31T15:00:00Z");
		insertTicket(userId, missionClaimOf(userId), TicketGrade.BRONZE, TicketStatus.RETURNED, "2026-10-31T15:00:00Z");
		insertTicket(userId, missionClaimOf(userId), TicketGrade.GOLD, TicketStatus.AVAILABLE, "2026-10-31T15:00:00Z");
		insertTicket(userId, missionClaimOf(userId), TicketGrade.SILVER, TicketStatus.AVAILABLE, "2026-09-30T15:00:00Z");
		insertTicket(userId, missionClaimOf(userId), TicketGrade.BRONZE, TicketStatus.SPENT, "2026-10-31T15:00:00Z");
		insertTicket(userId, missionClaimOf(userId), TicketGrade.BRONZE, TicketStatus.EXPIRED, "2026-08-31T15:00:00Z");
		// when
		List<TicketHolding> holdings = transaction.execute(status -> queryRepository.findHoldings(userId, SEPTEMBER));
		// then
		assertThat(holdings).extracting(TicketHolding::getGrade, TicketHolding::getExpiresAt, TicketHolding::getCount)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(TicketGrade.SILVER, Instant.parse("2026-09-30T15:00:00Z"), 1L),
						org.assertj.core.groups.Tuple.tuple(TicketGrade.BRONZE, Instant.parse("2026-10-31T15:00:00Z"), 2L),
						org.assertj.core.groups.Tuple.tuple(TicketGrade.GOLD, Instant.parse("2026-10-31T15:00:00Z"), 1L));
	}

	@Test
	@DisplayName("만료 처리 전이라도 만료 시각이 조회 시각 이전이거나 같은 응모권은 제외한다")
	void excludesTicketsPastExpiry() {
		// given
		insertTicket(userId, missionClaimOf(userId), TicketGrade.BRONZE, TicketStatus.AVAILABLE, "2026-09-15T03:00:00Z");
		insertTicket(userId, missionClaimOf(userId), TicketGrade.BRONZE, TicketStatus.AVAILABLE, "2026-09-15T03:00:01Z");
		// when
		List<TicketHolding> holdings = transaction.execute(status -> queryRepository.findHoldings(userId, SEPTEMBER));
		// then
		assertThat(holdings).singleElement().satisfies(holding -> {
			assertThat(holding.getExpiresAt()).isEqualTo(Instant.parse("2026-09-15T03:00:01Z"));
			assertThat(holding.getCount()).isEqualTo(1);
		});
	}

	@Test
	@DisplayName("응모권이 없는 사용자는 빈 목록이다")
	void noTickets() {
		// given
		// when
		List<TicketHolding> holdings = transaction.execute(status -> queryRepository.findHoldings(userId, SEPTEMBER));
		// then
		assertThat(holdings).isEmpty();
	}

	@Test
	@DisplayName("이력을 처리 시각·ID 역순으로 조회하고 지급 이력 값을 그대로 옮긴다")
	void findsHistoryNewestFirst() {
		// given
		clock.set(SEPTEMBER);
		GrantResult first = grantNewMissionClaim(userId, 1);
		clock.set(SEPTEMBER.plusSeconds(60));
		GrantResult second = grantNewMissionClaim(userId, 1);
		// when
		List<TicketHistoryView> history = findAll(userId, TicketHistoryFilter.none());
		// then
		assertThat(history).extracting(TicketHistoryView::getCreatedAt)
				.containsExactly(second.getGrantedAt(), first.getGrantedAt());
		TicketHistoryView latest = history.get(0);
		assertThat(latest.getOperationType()).isEqualTo(TicketOperationType.GRANT);
		assertThat(latest.getGrade()).isEqualTo(second.getGrade());
		assertThat(latest.getStatus()).isEqualTo(TicketStatus.AVAILABLE);
		assertThat(latest.getExpiresAt()).isEqualTo(second.getExpiresAt());
		assertThat(latest.getReason()).isEqualTo("테스트 보상");
		assertThat(latest.getEventId()).isNull();
		assertThat(latest.getEventEntryId()).isNull();
		assertThat(latest.getOriginalUseHistoryId()).isNull();
		assertThat(latest.getCorrectedHistoryId()).isNull();
	}

	@Test
	@DisplayName("생성 시각이 같은 이력이 많아도 커서로 넘기면 중복·누락 없이 같은 순서로 모두 조회된다")
	void cursorPagingIsStableWithTies() {
		// given: 처리 시각이 같은 이력 묶음 두 개(5건, 2건). 페이지 경계가 두 묶음 모두의 중간에 걸린다
		clock.set(SEPTEMBER);
		TicketGrantSeeds.GameParents game = seeds.gameParents(userId);
		TicketGrantSeeds.AttendanceParents attendance = seeds.attendanceParents(userId);
		transaction.executeWithoutResult(status -> {
			UUID claim = seeds.attendanceClaim(userId, attendance, 5);
			grantService.grant(command(userId, GrantSourceType.ATTENDANCE, claim, 5));
		});
		clock.set(SEPTEMBER.plusSeconds(1));
		transaction.executeWithoutResult(status -> {
			UUID claim = seeds.gameClaim(userId, game, 1);
			grantService.grant(command(userId, GrantSourceType.GAME, claim, 1));
		});
		grantNewMissionClaim(userId, 1);
		List<TicketHistoryView> expected = findAll(userId, TicketHistoryFilter.none());
		// when: 2건씩 끝까지 넘긴다
		List<TicketHistoryView> paged = new ArrayList<>();
		TicketHistoryCursor cursor = null;
		for (int page = 0; page < 10; page++) {
			TicketHistoryCursor after = cursor;
			List<TicketHistoryView> rows = transaction.execute(status ->
					queryRepository.findHistory(userId, TicketHistoryFilter.none(), after, 2));
			paged.addAll(rows);
			if (rows.size() < 2) {
				break;
			}
			cursor = rows.get(rows.size() - 1).cursor();
		}
		// then
		assertThat(expected).hasSize(7);
		assertThat(count("""
				select count(distinct h.created_at) from ticket_histories h
				join tickets t on t.id = h.ticket_id where t.user_id = ?
				""", bytes(userId))).isEqualTo(2);
		assertThat(paged).extracting(TicketHistoryView::getId)
				.doesNotHaveDuplicates()
				.containsExactlyElementsOf(expected.stream().map(TicketHistoryView::getId).toList());
	}

	@Test
	@DisplayName("처리 유형과 [from, to) 기간으로 거르고, 개수는 커서와 관계없이 조건에 맞는 전체 수다")
	void filtersByTypeAndPeriod() {
		// given
		clock.set(Instant.parse("2026-09-15T00:00:00Z"));
		GrantResult atFrom = grantNewMissionClaim(userId, 1);
		clock.set(Instant.parse("2026-09-15T01:00:00Z"));
		GrantResult inside = grantNewMissionClaim(userId, 1);
		clock.set(Instant.parse("2026-09-15T02:00:00Z"));
		grantNewMissionClaim(userId, 1);
		TicketHistoryFilter period = TicketHistoryFilter.of(null,
				Instant.parse("2026-09-15T00:00:00Z"), Instant.parse("2026-09-15T02:00:00Z"));
		// when
		List<TicketHistoryView> inPeriod = findAll(userId, period);
		long periodCount = transaction.execute(status -> queryRepository.countHistory(userId, period));
		List<TicketHistoryView> grants = findAll(userId,
				TicketHistoryFilter.of(TicketOperationType.GRANT, null, null));
		List<TicketHistoryView> uses = findAll(userId,
				TicketHistoryFilter.of(TicketOperationType.USE, null, null));
		long totalCount = transaction.execute(status ->
				queryRepository.countHistory(userId, TicketHistoryFilter.none()));
		// then: 시작 시각은 포함하고 끝 시각은 제외한다
		assertThat(inPeriod).extracting(TicketHistoryView::getCreatedAt)
				.containsExactly(inside.getGrantedAt(), atFrom.getGrantedAt());
		assertThat(periodCount).isEqualTo(2);
		assertThat(grants).hasSize(3);
		assertThat(uses).isEmpty();
		assertThat(totalCount).isEqualTo(3);
	}

	@Test
	@DisplayName("청구 종류에 따라 미션 ID·게임 ID·출석일을 청구 기록에서 채운다")
	void fillsDerivedSourceFields() {
		// given
		TicketGrantSeeds.MissionParents mission = seeds.missionParents(userId);
		TicketGrantSeeds.AttendanceParents attendance = seeds.attendanceParents(userId);
		TicketGrantSeeds.GameParents game = seeds.gameParents(userId);
		transaction.executeWithoutResult(status -> {
			grantService.grant(command(userId, GrantSourceType.MISSION, seeds.missionClaim(userId, mission, 1), 1));
			grantService.grant(command(userId, GrantSourceType.ATTENDANCE,
					seeds.attendanceClaim(userId, attendance, 1), 1));
			grantService.grant(command(userId, GrantSourceType.GAME, seeds.gameClaim(userId, game, 1), 1));
		});
		LocalDate attendanceDate = jdbc.queryForObject("select attendance_date from attendances where id = ?",
				LocalDate.class, bytes(attendance.attendanceId()));
		// when
		List<TicketHistoryView> history = findAll(userId, TicketHistoryFilter.none());
		// then
		assertThat(history).hasSize(3);
		assertThat(history).filteredOn(view -> view.getMissionId() != null).singleElement().satisfies(view -> {
			assertThat(view.getMissionId()).isEqualTo(mission.missionId());
			assertThat(view.getGameId()).isNull();
			assertThat(view.getAttendanceDate()).isNull();
		});
		assertThat(history).filteredOn(view -> view.getAttendanceDate() != null).singleElement().satisfies(view -> {
			assertThat(view.getAttendanceDate()).isEqualTo(attendanceDate);
			assertThat(view.getGrade()).isEqualTo(TicketGrade.BRONZE);
			assertThat(view.getMissionId()).isNull();
		});
		assertThat(history).filteredOn(view -> view.getGameId() != null).singleElement().satisfies(view -> {
			assertThat(view.getGameId()).isEqualTo(game.gameId());
			assertThat(view.getMissionId()).isNull();
		});
	}

	@Test
	@DisplayName("다른 사용자의 응모권과 이력은 조회되지 않는다")
	void isolatesUsers() {
		// given
		UUID other = seeds.user();
		grantNewMissionClaim(other, 1);
		GrantResult mine = grantNewMissionClaim(userId, 1);
		// when
		List<TicketHistoryView> history = findAll(userId, TicketHistoryFilter.none());
		List<TicketHolding> holdings = transaction.execute(status ->
				queryRepository.findHoldings(userId, mine.getGrantedAt()));
		// then
		assertThat(history).hasSize(1);
		assertThat(holdings).extracting(TicketHolding::getCount).containsExactly(1L);
	}

	private UUID missionClaimOf(UUID user) {
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(user);
		return transaction.execute(status -> seeds.missionClaim(user, parents, 1));
	}

	private List<TicketHistoryView> findAll(UUID user, TicketHistoryFilter filter) {
		return transaction.execute(status -> queryRepository.findHistory(user, filter, null, 100));
	}
}
