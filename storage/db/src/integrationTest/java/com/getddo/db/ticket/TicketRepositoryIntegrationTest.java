package com.getddo.db.ticket;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceClaim;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.TicketLedger;
import com.getddo.core.ticket.domain.TicketLedgerAllocation;
import com.getddo.core.ticket.domain.TicketTransactionType;
import com.getddo.core.ticket.domain.TicketWallet;
import com.getddo.core.ticket.domain.TicketWalletPeriod;
import com.getddo.core.ticket.domain.TicketWalletStatus;
import com.getddo.core.ticket.repository.GrantSourceRepository;
import com.getddo.core.ticket.repository.TicketLedgerAllocationRepository;
import com.getddo.core.ticket.repository.TicketLedgerRepository;
import com.getddo.core.ticket.repository.TicketWalletRepository;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 응모권 Entity 매핑과 저장소 구현을 Flyway로 만든 실제 MySQL 스키마에서 검증한다. */
class TicketRepositoryIntegrationTest extends TicketIntegrationTestSupport {

	private static final Instant GRANTED_AT = Instant.parse("2026-09-15T03:00:00.123456Z");
	private static final TicketWalletPeriod SEPTEMBER = new TicketWalletPeriod(
			LocalDate.parse("2026-09-01"), Instant.parse("2026-09-30T15:00:00Z"));

	@Autowired
	private TicketWalletRepository walletRepository;
	@Autowired
	private TicketLedgerRepository ledgerRepository;
	@Autowired
	private TicketLedgerAllocationRepository allocationRepository;
	@Autowired
	private GrantSourceRepository grantSourceRepository;
	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	@DisplayName("지갑이 없으면 첫 입금 시각으로 만들고 잠가 반환한다")
	void createsWalletWhenAbsent() {
		// given
		// when
		TicketWallet wallet = transaction.execute(status ->
				walletRepository.getOrCreateForUpdate(userId, SEPTEMBER, GRANTED_AT));
		// then
		assertThat(wallet.getId().version()).isEqualTo(7);
		assertThat(wallet.getUserId()).isEqualTo(userId);
		assertThat(wallet.getExpiryMonth()).isEqualTo(SEPTEMBER.getExpiryMonth());
		assertThat(wallet.getValidFrom()).isEqualTo(GRANTED_AT);
		assertThat(wallet.getExpiresAt()).isEqualTo(SEPTEMBER.getExpiresAt());
		assertThat(wallet.getBalance()).isZero();
		assertThat(wallet.getStatus()).isEqualTo(TicketWalletStatus.ACTIVE);
		assertThat(wallet.getVersion()).isZero();
		assertThat(jdbc.queryForObject(
				"select created_at from ticket_wallets where id = ?", LocalDateTime.class, bytes(wallet.getId())))
				.isEqualTo(LocalDateTime.parse("2026-09-15T03:00:00.123456"));
	}

	@Test
	@DisplayName("같은 사용자·만료 묶음의 지갑이 이미 있으면 새로 만들지 않고 기존 지갑을 반환한다")
	void returnsExistingWallet() {
		// given
		TicketWallet first = transaction.execute(status ->
				walletRepository.getOrCreateForUpdate(userId, SEPTEMBER, GRANTED_AT));
		// when
		TicketWallet second = transaction.execute(status ->
				walletRepository.getOrCreateForUpdate(userId, SEPTEMBER, GRANTED_AT.plusSeconds(60)));
		// then
		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(second.getValidFrom()).isEqualTo(GRANTED_AT);
		assertThat(countWallets()).isEqualTo(1);
	}

	@Test
	@DisplayName("잠근 지갑에 반영한 잔액과 version이 커밋 후 저장된다")
	void savesDepositedWallet() {
		// given
		// when
		TicketWallet saved = transaction.execute(status -> {
			TicketWallet wallet = walletRepository.getOrCreateForUpdate(userId, SEPTEMBER, GRANTED_AT);
			TicketWallet deposited = wallet.deposit(3);
			walletRepository.save(deposited);
			return deposited;
		});
		// then
		assertThat(jdbc.queryForObject("select balance from ticket_wallets where id = ?", Long.class,
				bytes(saved.getId()))).isEqualTo(3L);
		assertThat(jdbc.queryForObject("select version from ticket_wallets where id = ?", Long.class,
				bytes(saved.getId()))).isEqualTo(1L);
	}

	@Test
	@DisplayName("이전 트랜잭션에서 잠갔던 지갑은 현재 트랜잭션에서 갱신을 거절하고 잔액·version을 바꾸지 않는다")
	void rejectsSavingWalletLockedByPreviousTransaction() {
		// given: 앞 트랜잭션의 잠금과 잠금 기록은 커밋과 함께 사라진다
		TicketWallet wallet = transaction.execute(status ->
				walletRepository.getOrCreateForUpdate(userId, SEPTEMBER, GRANTED_AT));
		// when
		// then
		assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
				walletRepository.save(wallet.deposit(1))))
				.isInstanceOf(InvalidDataAccessApiUsageException.class)
				.hasCauseInstanceOf(IllegalStateException.class);
		assertThat(walletBalance(wallet.getId())).isZero();
		assertThat(walletVersion(wallet.getId())).isZero();
	}

	@Test
	@DisplayName("REQUIRES_NEW 안쪽 트랜잭션은 바깥 트랜잭션이 잠근 지갑을 갱신할 수 없고, 바깥은 복귀 후에도 갱신할 수 있다")
	void innerTransactionCannotUseOuterLock() {
		// given
		TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
		requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		// when
		transaction.executeWithoutResult(outer -> {
			TicketWallet locked = walletRepository.getOrCreateForUpdate(userId, SEPTEMBER, GRANTED_AT);
			// then
			assertThatThrownBy(() -> requiresNew.executeWithoutResult(inner ->
					walletRepository.save(locked.deposit(1))))
					.isInstanceOf(InvalidDataAccessApiUsageException.class)
					.hasCauseInstanceOf(IllegalStateException.class);
			walletRepository.save(locked.deposit(2));
		});
		assertThat(count("select balance from ticket_wallets where user_id = ?", bytes(userId))).isEqualTo(2);
	}

	@Test
	@DisplayName("지급 원장과 자기 배분 행을 저장하고 멱등키로 같은 값을 다시 읽는다")
	void savesAndFindsGrantLedger() {
		// given
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		// when
		TicketLedger saved = transaction.execute(status -> {
			UUID claimId = seeds.missionClaim(userId, parents, 2);
			TicketWallet wallet = walletRepository.getOrCreateForUpdate(userId, SEPTEMBER, GRANTED_AT).deposit(2);
			walletRepository.save(wallet);
			TicketLedger ledger = ledgerRepository.save(new TicketLedger(null, wallet.getId(), userId,
					TicketTransactionType.GRANT, 2, "GRANT:MISSION:" + claimId, "테스트 미션", GRANTED_AT,
					wallet.getBalance(), wallet.getVersion(), wallet.getExpiresAt(),
					new GrantSource(GrantSourceType.MISSION, claimId)));
			allocationRepository.save(TicketLedgerAllocation.selfCredit(ledger));
			return ledger;
		});
		// then
		assertThat(saved.getId().version()).isEqualTo(7);
		TicketLedger found = transaction.execute(status ->
				ledgerRepository.findByIdempotencyKey(saved.getIdempotencyKey()).orElseThrow());
		assertThat(found).isEqualTo(saved);
		assertThat(jdbc.queryForObject("""
				select count(*) from ticket_ledger_allocations
				where ledger_id = ? and source_credit_ledger_id = ? and original_grant_id = ?
				  and quantity = 2 and created_at = '2026-09-15 03:00:00.123456'
				""", Long.class, bytes(saved.getId()), bytes(saved.getId()), bytes(saved.getId()))).isEqualTo(1L);
		assertThat(jdbc.queryForObject(
				"select expires_at from ticket_ledger where id = ?", LocalDateTime.class, bytes(saved.getId())))
				.isEqualTo(LocalDateTime.parse("2026-09-30T15:00:00"));
	}

	@Test
	@DisplayName("같은 트랜잭션에서 방금 저장한 청구를 종류별로 조회하고 없는 청구는 빈 값을 반환한다")
	void findsClaimsOfEachType() {
		// given
		TicketGrantSeeds.MissionParents mission = seeds.missionParents(userId);
		TicketGrantSeeds.AttendanceParents attendance = seeds.attendanceParents(userId);
		TicketGrantSeeds.GameParents game = seeds.gameParents(userId);
		// when
		// then
		transaction.executeWithoutResult(status -> {
			UUID missionClaim = seeds.missionClaim(userId, mission, 3);
			UUID attendanceClaim = seeds.attendanceClaim(userId, attendance, 1);
			UUID gameClaim = seeds.gameClaim(userId, game, 1);
			assertThat(grantSourceRepository.find(new GrantSource(GrantSourceType.MISSION, missionClaim)))
					.contains(new GrantSourceClaim(userId, 3));
			assertThat(grantSourceRepository.find(new GrantSource(GrantSourceType.ATTENDANCE, attendanceClaim)))
					.contains(new GrantSourceClaim(userId, 1));
			assertThat(grantSourceRepository.find(new GrantSource(GrantSourceType.GAME, gameClaim)))
					.contains(new GrantSourceClaim(userId, 1));
			assertThat(grantSourceRepository.find(new GrantSource(GrantSourceType.GAME, missionClaim)))
					.isEmpty();
		});
	}

	private long countWallets() {
		return walletCount(userId);
	}
}
