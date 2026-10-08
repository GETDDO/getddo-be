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
import com.getddo.core.ticket.domain.TicketLedgerCursor;
import com.getddo.core.ticket.domain.TicketLedgerFilter;
import com.getddo.core.ticket.domain.TicketTransactionType;
import com.getddo.core.ticket.domain.TicketTransactionView;
import com.getddo.core.ticket.domain.TicketWallet;
import com.getddo.core.ticket.domain.TicketWalletStatus;
import com.getddo.core.ticket.repository.TicketQueryRepository;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;

/** 화면용 응모권 조회 SQL을 Flyway로 만든 실제 MySQL 스키마에서 검증한다. */
class TicketQueryRepositoryIntegrationTest extends TicketIntegrationTestSupport {

	private static final Instant SEPTEMBER = Instant.parse("2026-09-15T03:00:00Z");

	@Autowired
	private TicketQueryRepository queryRepository;

	@Test
	@DisplayName("만료된 지갑을 포함한 모든 지갑을 만료월 최신순으로 조회한다")
	void findsAllWalletsNewestExpiryFirst() {
		// given
		UUID expired = insertWallet(userId, "2026-07-01", "2026-07-31T15:00:00Z", 3, TicketWalletStatus.EXPIRED);
		clock.set(SEPTEMBER);
		GrantResult september = grantNewMissionClaim(userId, 2);
		clock.set(Instant.parse("2026-10-05T00:00:00Z"));
		GrantResult october = grantNewMissionClaim(userId, 1);
		// when
		List<TicketWallet> wallets = transaction.execute(status -> queryRepository.findWallets(userId));
		// then
		assertThat(wallets).extracting(TicketWallet::getId)
				.containsExactly(october.getWalletId(), september.getWalletId(), expired);
		assertThat(wallets).extracting(TicketWallet::getBalance).containsExactly(1L, 2L, 3L);
		assertThat(wallets.get(2).getStatus()).isEqualTo(TicketWalletStatus.EXPIRED);
	}

	@Test
	@DisplayName("지갑이 없는 사용자는 빈 목록이다")
	void noWallets() {
		// given
		// when
		List<TicketWallet> wallets = transaction.execute(status -> queryRepository.findWallets(userId));
		// then
		assertThat(wallets).isEmpty();
	}

	@Test
	@DisplayName("이력을 생성 시각·ID 역순으로 조회하고 원장 값을 그대로 옮긴다")
	void findsLedgerNewestFirst() {
		// given
		clock.set(SEPTEMBER);
		GrantResult first = grantNewMissionClaim(userId, 2);
		clock.set(SEPTEMBER.plusSeconds(60));
		GrantResult second = grantNewMissionClaim(userId, 1);
		// when
		List<TicketTransactionView> ledger = findAll(userId, TicketLedgerFilter.none());
		// then
		assertThat(ledger).extracting(TicketTransactionView::getId).containsExactly(second.getLedgerId(), first.getLedgerId());
		TicketTransactionView latest = ledger.get(0);
		assertThat(latest.getWalletId()).isEqualTo(second.getWalletId());
		assertThat(latest.getTransactionType()).isEqualTo(TicketTransactionType.GRANT);
		assertThat(latest.getQuantity()).isEqualTo(1);
		assertThat(latest.getBalanceAfter()).isEqualTo(3);
		assertThat(latest.getReason()).isEqualTo("테스트 보상");
		assertThat(latest.getCreatedAt()).isEqualTo(second.getGrantedAt());
		assertThat(latest.getExpiresAt()).isEqualTo(second.getExpiresAt());
		assertThat(latest.getEventId()).isNull();
		assertThat(latest.getEventEntryId()).isNull();
		assertThat(latest.getRelatedLedgerId()).isNull();
		assertThat(latest.getRefundOfId()).isNull();
	}

	@Test
	@DisplayName("생성 시각이 같은 이력이 많아도 커서로 넘기면 중복·누락 없이 같은 순서로 모두 조회된다")
	void cursorPagingIsStableWithTies() {
		// given: 생성 시각이 같은 이력 묶음 두 개(5건, 2건). 페이지 경계가 두 묶음 모두의 중간에 걸린다
		clock.set(SEPTEMBER);
		TicketGrantSeeds.GameParents game = seeds.gameParents(userId);
		transaction.executeWithoutResult(status -> {
			for (int i = 0; i < 5; i++) {
				UUID claim = seeds.gameClaim(userId, game, 1);
				grantService.grant(command(userId, GrantSourceType.GAME, claim, 1));
			}
		});
		clock.set(SEPTEMBER.plusSeconds(1));
		grantNewMissionClaim(userId, 1);
		grantNewMissionClaim(userId, 1);
		List<TicketTransactionView> expected = findAll(userId, TicketLedgerFilter.none());
		// when: 2건씩 끝까지 넘긴다
		List<TicketTransactionView> paged = new ArrayList<>();
		TicketLedgerCursor cursor = null;
		for (int page = 0; page < 10; page++) {
			TicketLedgerCursor after = cursor;
			List<TicketTransactionView> rows = transaction.execute(status ->
					queryRepository.findLedger(userId, TicketLedgerFilter.none(), after, 2));
			paged.addAll(rows);
			if (rows.size() < 2) {
				break;
			}
			cursor = rows.get(rows.size() - 1).cursor();
		}
		// then
		assertThat(expected).hasSize(7);
		assertThat(count("select count(distinct created_at) from ticket_ledger where user_id = ?", bytes(userId)))
				.isEqualTo(2);
		assertThat(paged).extracting(TicketTransactionView::getId)
				.doesNotHaveDuplicates()
				.containsExactlyElementsOf(expected.stream().map(TicketTransactionView::getId).toList());
	}

	@Test
	@DisplayName("거래 유형과 [from, to) 기간으로 거르고, 개수는 커서와 관계없이 조건에 맞는 전체 수다")
	void filtersByTypeAndPeriod() {
		// given
		clock.set(Instant.parse("2026-09-15T00:00:00Z"));
		GrantResult atFrom = grantNewMissionClaim(userId, 1);
		clock.set(Instant.parse("2026-09-15T01:00:00Z"));
		GrantResult inside = grantNewMissionClaim(userId, 1);
		clock.set(Instant.parse("2026-09-15T02:00:00Z"));
		grantNewMissionClaim(userId, 1);
		TicketLedgerFilter period = TicketLedgerFilter.of(null,
				Instant.parse("2026-09-15T00:00:00Z"), Instant.parse("2026-09-15T02:00:00Z"));
		// when
		List<TicketTransactionView> inPeriod = findAll(userId, period);
		long periodCount = transaction.execute(status -> queryRepository.countLedger(userId, period));
		List<TicketTransactionView> grants = findAll(userId,
				TicketLedgerFilter.of(TicketTransactionType.GRANT, null, null));
		List<TicketTransactionView> spends = findAll(userId,
				TicketLedgerFilter.of(TicketTransactionType.SPEND, null, null));
		long totalCount = transaction.execute(status ->
				queryRepository.countLedger(userId, TicketLedgerFilter.none()));
		// then: 시작 시각은 포함하고 끝 시각은 제외한다
		assertThat(inPeriod).extracting(TicketTransactionView::getId)
				.containsExactly(inside.getLedgerId(), atFrom.getLedgerId());
		assertThat(periodCount).isEqualTo(2);
		assertThat(grants).hasSize(3);
		assertThat(spends).isEmpty();
		assertThat(totalCount).isEqualTo(3);
	}

	@Test
	@DisplayName("청구 종류에 따라 미션 ID·게임 ID·출석일을 청구 기록에서 채운다")
	void fillsDerivedSourceFields() {
		// given
		TicketGrantSeeds.MissionParents mission = seeds.missionParents(userId);
		TicketGrantSeeds.AttendanceParents attendance = seeds.attendanceParents(userId);
		TicketGrantSeeds.GameParents game = seeds.gameParents(userId);
		List<GrantResult> results = transaction.execute(status -> List.of(
				grantService.grant(command(userId, GrantSourceType.MISSION,
						seeds.missionClaim(userId, mission, 1), 1)),
				grantService.grant(command(userId, GrantSourceType.ATTENDANCE,
						seeds.attendanceClaim(userId, attendance, 1), 1)),
				grantService.grant(command(userId, GrantSourceType.GAME,
						seeds.gameClaim(userId, game, 1), 1))));
		LocalDate attendanceDate = jdbc.queryForObject("select attendance_date from attendances where id = ?",
				LocalDate.class, bytes(attendance.attendanceId()));
		// when
		List<TicketTransactionView> ledger = findAll(userId, TicketLedgerFilter.none());
		// then
		TicketTransactionView missionRow = row(ledger, results.get(0));
		TicketTransactionView attendanceRow = row(ledger, results.get(1));
		TicketTransactionView gameRow = row(ledger, results.get(2));
		assertThat(missionRow.getMissionId()).isEqualTo(mission.missionId());
		assertThat(missionRow.getGameId()).isNull();
		assertThat(missionRow.getAttendanceDate()).isNull();
		assertThat(attendanceRow.getAttendanceDate()).isEqualTo(attendanceDate);
		assertThat(attendanceRow.getMissionId()).isNull();
		assertThat(gameRow.getGameId()).isEqualTo(game.gameId());
		assertThat(gameRow.getMissionId()).isNull();
	}

	@Test
	@DisplayName("다른 사용자의 지갑과 이력은 조회되지 않는다")
	void isolatesUsers() {
		// given
		UUID other = seeds.user();
		grantNewMissionClaim(other, 5);
		GrantResult mine = grantNewMissionClaim(userId, 1);
		// when
		List<TicketTransactionView> ledger = findAll(userId, TicketLedgerFilter.none());
		List<TicketWallet> wallets = transaction.execute(status -> queryRepository.findWallets(userId));
		// then
		assertThat(ledger).extracting(TicketTransactionView::getId).containsExactly(mine.getLedgerId());
		assertThat(wallets).extracting(TicketWallet::getId).containsExactly(mine.getWalletId());
	}

	private List<TicketTransactionView> findAll(UUID user, TicketLedgerFilter filter) {
		return transaction.execute(status -> queryRepository.findLedger(user, filter, null, 100));
	}

	private static TicketTransactionView row(List<TicketTransactionView> ledger, GrantResult result) {
		return ledger.stream().filter(view -> view.getId().equals(result.getLedgerId())).findFirst().orElseThrow();
	}

}
