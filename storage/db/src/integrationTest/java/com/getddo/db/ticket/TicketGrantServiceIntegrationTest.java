package com.getddo.db.ticket;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceType;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;

/** 실제 MySQL에서 지급 한 건이 지갑·원장·배분에 남기는 결과와 그 정합성을 검증한다. */
class TicketGrantServiceIntegrationTest extends TicketIntegrationTestSupport {

	@Test
	@DisplayName("신규 지급 한 번에 지갑·원장·배분 세 행이 생기고 생성 시각이 모두 지급 시각과 같다")
	void newGrantCreatesThreeRowsWithSameCreatedAt() {
		// given: 시계를 읽을 때마다 1ms씩 진행해 시각을 두 번 이상 읽으면 값이 달라지게 한다
		clock.tickEveryRead(true);
		// when
		GrantResult result = grantNewMissionClaim(userId, 2);
		// then
		assertThat(result.replayed()).isFalse();
		assertThat(walletCount(userId)).isEqualTo(1);
		assertThat(ledgerCount(userId)).isEqualTo(1);
		assertThat(allocationCount(userId)).isEqualTo(1);

		Instant grantedAt = result.grantedAt();
		assertThat(utc("select created_at from ticket_wallets where id = ?", bytes(result.walletId())))
				.isEqualTo(grantedAt);
		assertThat(utc("select valid_from from ticket_wallets where id = ?", bytes(result.walletId())))
				.isEqualTo(grantedAt);
		assertThat(utc("select created_at from ticket_ledger where id = ?", bytes(result.ledgerId())))
				.isEqualTo(grantedAt);
		assertThat(utc("select created_at from ticket_ledger_allocations where ledger_id = ?",
				bytes(result.ledgerId()))).isEqualTo(grantedAt);
	}

	@Test
	@DisplayName("원장의 balance_after·wallet_version이 지갑의 잔액·version과 일치하고 자기 배분 행이 지급 수량을 가진다")
	void ledgerMatchesWalletAndSelfAllocation() {
		// given
		// when
		GrantResult result = grantNewMissionClaim(userId, 3);
		// then
		byte[] ledgerId = bytes(result.ledgerId());
		assertThat(count("select balance_after from ticket_ledger where id = ?", ledgerId))
				.isEqualTo(walletBalance(result.walletId()))
				.isEqualTo(result.balanceAfter())
				.isEqualTo(3);
		assertThat(count("select wallet_version from ticket_ledger where id = ?", ledgerId))
				.isEqualTo(walletVersion(result.walletId()))
				.isEqualTo(1);
		assertThat(utc("select expires_at from ticket_ledger where id = ?", ledgerId))
				.isEqualTo(utc("select expires_at from ticket_wallets where id = ?", bytes(result.walletId())))
				.isEqualTo(result.expiresAt());
		assertThat(count("""
				select count(*) from ticket_ledger_allocations
				where ledger_id = ? and source_credit_ledger_id = ? and original_grant_id = ? and quantity = 3
				""", ledgerId, ledgerId, ledgerId)).isEqualTo(1);
		assertThat(count("""
				select count(*) from ticket_ledger
				where id = ? and transaction_type = 'GRANT' and actor_id is null and quantity = 3
				""", ledgerId)).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 월 두 번째 지급은 같은 지갑에 누적되고 wallet_version이 1, 2로 이어진다")
	void secondGrantInSameMonthUsesSameWallet() {
		// given
		GrantResult first = grantNewMissionClaim(userId, 2);
		clock.set(TicketIntegrationTestApplication.INITIAL_TIME.plusSeconds(3600));
		// when
		GrantResult second = grantNewMissionClaim(userId, 3);
		// then
		assertThat(second.walletId()).isEqualTo(first.walletId());
		assertThat(walletCount(userId)).isEqualTo(1);
		assertThat(ledgerVersions(userId)).containsExactly(1L, 2L);
		assertThat(ledgerBalances(userId)).containsExactly(2L, 5L);
		assertThat(walletBalance(first.walletId())).isEqualTo(5);
		assertThat(walletVersion(first.walletId())).isEqualTo(2);
		// 첫 입금 시각은 유지되고 수정 시각만 두 번째 지급 시점으로 바뀐다
		assertThat(utc("select valid_from from ticket_wallets where id = ?", bytes(first.walletId())))
				.isEqualTo(first.grantedAt());
		assertThat(utc("select created_at from ticket_wallets where id = ?", bytes(first.walletId())))
				.isEqualTo(first.grantedAt());
		assertThat(utc("select updated_at from ticket_wallets where id = ?", bytes(first.walletId())))
				.isEqualTo(second.grantedAt());
	}

	@Test
	@DisplayName("한 트랜잭션에서 같은 지갑에 두 번 지급해도 wallet_version이 1, 2로 기록된다")
	void twoGrantsInOneTransaction() {
		// given
		TicketGrantSeeds.AttendanceParents attendance = seeds.attendanceParents(userId);
		TicketGrantSeeds.GameParents game = seeds.gameParents(userId);
		// when
		transaction.executeWithoutResult(status -> {
			UUID attendanceClaim = seeds.attendanceClaim(userId, attendance, 1);
			grantService.grant(command(userId, GrantSourceType.ATTENDANCE, attendanceClaim, 1));
			UUID gameClaim = seeds.gameClaim(userId, game, 1);
			grantService.grant(command(userId, GrantSourceType.GAME, gameClaim, 1));
		});
		// then
		assertThat(walletCount(userId)).isEqualTo(1);
		assertThat(ledgerVersions(userId)).containsExactly(1L, 2L);
		assertThat(ledgerBalances(userId)).containsExactly(1L, 2L);
	}

	@Test
	@DisplayName("같은 청구로 다시 호출하면 추가 지급 없이 기존 결과를 replayed=true로 반환하고 지갑 version이 오르지 않는다")
	void replayDoesNotChangeWallet() {
		// given
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		UUID claimId = transaction.execute(status -> seeds.missionClaim(userId, parents, 2));
		GrantResult first = transaction.execute(status ->
				grantService.grant(command(userId, GrantSourceType.MISSION, claimId, 2)));
		clock.set(TicketIntegrationTestApplication.INITIAL_TIME.plusSeconds(60));
		// when
		GrantResult replayed = transaction.execute(status ->
				grantService.grant(command(userId, GrantSourceType.MISSION, claimId, 2)));
		// then
		assertThat(replayed).isEqualTo(new GrantResult(first.ledgerId(), first.walletId(), 2, 2,
				first.grantedAt(), first.expiresAt(), true));
		assertThat(ledgerCount(userId)).isEqualTo(1);
		assertThat(allocationCount(userId)).isEqualTo(1);
		assertThat(walletBalance(first.walletId())).isEqualTo(2);
		assertThat(walletVersion(first.walletId())).isEqualTo(1);
	}

	@Test
	@DisplayName("여러 지급 후 원장 수량 합계가 지갑 잔액과 같고, 원장마다 배분 합계가 원장 수량과 같다")
	void sumsAreConsistent() {
		// given
		grantNewMissionClaim(userId, 2);
		grantNewMissionClaim(userId, 1);
		TicketGrantSeeds.GameParents game = seeds.gameParents(userId);
		transaction.executeWithoutResult(status -> {
			UUID claim = seeds.gameClaim(userId, game, 1);
			grantService.grant(command(userId, GrantSourceType.GAME, claim, 1));
		});
		// when
		long ledgerSum = count("select sum(quantity) from ticket_ledger where user_id = ?", bytes(userId));
		long walletSum = count("select sum(balance) from ticket_wallets where user_id = ?", bytes(userId));
		long mismatchedLedgers = count("""
				select count(*) from ticket_ledger l
				where l.user_id = ?
				  and l.quantity <> (select coalesce(sum(a.quantity), 0) from ticket_ledger_allocations a
				                     where a.source_credit_ledger_id = l.id)
				""", bytes(userId));
		// then
		assertThat(ledgerSum).isEqualTo(walletSum).isEqualTo(4);
		assertThat(mismatchedLedgers).isZero();
	}

	@Test
	@DisplayName("KST 9/30 23:59:59.999와 10/1 00:00:00 지급은 다른 지갑·다른 만료 시각을 쓴다")
	void monthBoundaryUsesDifferentWallets() {
		// given
		clock.set(Instant.parse("2026-09-30T14:59:59.999Z"));
		GrantResult september = grantNewMissionClaim(userId, 1);
		clock.set(Instant.parse("2026-09-30T15:00:00Z"));
		// when
		GrantResult october = grantNewMissionClaim(userId, 1);
		// then
		assertThat(october.walletId()).isNotEqualTo(september.walletId());
		assertThat(september.expiresAt()).isEqualTo(Instant.parse("2026-09-30T15:00:00Z"));
		assertThat(october.expiresAt()).isEqualTo(Instant.parse("2026-10-31T15:00:00Z"));
		assertThat(jdbc.queryForObject("select expiry_month from ticket_wallets where id = ?", LocalDate.class,
				bytes(september.walletId()))).isEqualTo(LocalDate.parse("2026-09-01"));
		assertThat(jdbc.queryForObject("select expiry_month from ticket_wallets where id = ?", LocalDate.class,
				bytes(october.walletId()))).isEqualTo(LocalDate.parse("2026-10-01"));
		assertThat(walletCount(userId)).isEqualTo(2);
	}

	@Test
	@DisplayName("청구 종류마다 원장의 해당 청구 참조 컬럼 하나만 채워진다")
	void fillsOnlyMatchingClaimColumn() {
		// given
		TicketGrantSeeds.MissionParents mission = seeds.missionParents(userId);
		TicketGrantSeeds.AttendanceParents attendance = seeds.attendanceParents(userId);
		TicketGrantSeeds.GameParents game = seeds.gameParents(userId);
		// when
		List<GrantResult> results = transaction.execute(status -> {
			UUID missionClaim = seeds.missionClaim(userId, mission, 1);
			UUID attendanceClaim = seeds.attendanceClaim(userId, attendance, 1);
			UUID gameClaim = seeds.gameClaim(userId, game, 1);
			return List.of(
					grantService.grant(command(userId, GrantSourceType.MISSION, missionClaim, 1)),
					grantService.grant(command(userId, GrantSourceType.ATTENDANCE, attendanceClaim, 1)),
					grantService.grant(command(userId, GrantSourceType.GAME, gameClaim, 1)));
		});
		// then
		assertThat(filledClaimColumn(results.get(0).ledgerId())).isEqualTo("MISSION");
		assertThat(filledClaimColumn(results.get(1).ledgerId())).isEqualTo("ATTENDANCE");
		assertThat(filledClaimColumn(results.get(2).ledgerId())).isEqualTo("GAME");
	}

	@Test
	@DisplayName("findGrant는 지급 전 빈 값, 지급 후 같은 결과를 replayed=true로 트랜잭션 유무와 관계없이 반환한다")
	void findGrantWithAndWithoutTransaction() {
		// given
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		UUID claimId = transaction.execute(status -> seeds.missionClaim(userId, parents, 1));
		GrantSource source = new GrantSource(GrantSourceType.MISSION, claimId);
		assertThat(grantService.findGrant(source)).isEmpty();
		GrantResult granted = transaction.execute(status ->
				grantService.grant(command(userId, GrantSourceType.MISSION, claimId, 1)));
		GrantResult expected = new GrantResult(granted.ledgerId(), granted.walletId(), granted.quantity(),
				granted.balanceAfter(), granted.grantedAt(), granted.expiresAt(), true);
		// when
		Optional<GrantResult> withoutTransaction = grantService.findGrant(source);
		Optional<GrantResult> inTransaction = transaction.execute(status -> grantService.findGrant(source));
		// then
		assertThat(withoutTransaction).contains(expected);
		assertThat(inTransaction).contains(expected);
	}

	private List<Long> ledgerVersions(UUID user) {
		return jdbc.queryForList("select wallet_version from ticket_ledger where user_id = ? order by wallet_version",
				Long.class, bytes(user));
	}

	private List<Long> ledgerBalances(UUID user) {
		return jdbc.queryForList("select balance_after from ticket_ledger where user_id = ? order by wallet_version",
				Long.class, bytes(user));
	}

	private String filledClaimColumn(UUID ledgerId) {
		return jdbc.queryForObject("""
				select concat_ws(',',
				  if(mission_reward_claim_id is null, null, 'MISSION'),
				  if(attendance_reward_claim_id is null, null, 'ATTENDANCE'),
				  if(game_reward_claim_id is null, null, 'GAME'))
				from ticket_ledger where id = ?
				""", String.class, bytes(ledgerId));
	}
}
