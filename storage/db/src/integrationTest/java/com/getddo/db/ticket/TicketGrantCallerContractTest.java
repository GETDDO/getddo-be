package com.getddo.db.ticket;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.IllegalTransactionStateException;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.exception.TicketErrorCode;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static com.getddo.db.ticket.TicketGrantSeeds.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 설계 문서 §4 호출 규약을 호출자 입장에서 실제 트랜잭션으로 검증한다. */
class TicketGrantCallerContractTest extends TicketIntegrationTestSupport {

	private static final long WAIT_SECONDS = 30;

	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("트랜잭션 없이 grant를 호출하면 실패하고 아무 행도 남지 않는다")
	void grantRequiresTransaction() {
		// given
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		UUID claimId = seeds.missionClaim(userId, parents, 1);
		// when
		// then
		assertThatThrownBy(() -> grantService.grant(command(userId, GrantSourceType.MISSION, claimId, 1)))
				.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(walletCount(userId)).isZero();
		assertThat(ledgerCount(userId)).isZero();
	}

	@Test
	@DisplayName("호출자가 JPA로 저장만 하고 flush하지 않은 청구도 grant가 찾아 지급한다")
	void seesClaimPersistedButNotFlushed() {
		// given
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		record Granted(UUID claimId, GrantResult result) {
		}
		// when
		Granted granted = transaction.execute(status -> {
			PendingMissionClaim claim = new PendingMissionClaim(userId, parents, 2);
			entityManager.persist(claim);
			// 아직 INSERT가 나가지 않았음을 같은 연결에서 확인한다
			assertThat(count("select count(*) from mission_reward_claims where id = ?", bytes(claim.getId())))
					.isZero();
			return new Granted(claim.getId(),
					grantService.grant(command(userId, GrantSourceType.MISSION, claim.getId(), 2)));
		});
		// then
		assertThat(granted.result().isReplayed()).isFalse();
		UUID ledgerClaimId = uuid(jdbc.queryForObject(
				"select mission_reward_claim_id from ticket_ledger where id = ?", byte[].class,
				bytes(granted.result().getLedgerId())));
		assertThat(ledgerClaimId).isEqualTo(granted.claimId());
	}

	@Test
	@DisplayName("JPA로 저장한 중복 청구의 UNIQUE 위반은 grant 호출 중에 나오고, 롤백 후 새 트랜잭션 재처리가 기존 결과를 반환한다")
	void duplicateClaimFailsInsideGrantAndRetryReplays() {
		// given: 같은 미션의 청구가 이미 지급·커밋되어 있다
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		GrantResult existing = completeMission(parents, () -> { });
		// when: 동시 요청이 사전 조회를 지나친 것처럼 중복 청구를 JPA로 저장하고 grant를 호출한다
		transaction.executeWithoutResult(status -> {
			PendingMissionClaim duplicate = new PendingMissionClaim(userId, parents, 1);
			entityManager.persist(duplicate);
			// then: 청구 INSERT가 grant 안의 flush에서 나가며 UNIQUE 위반이 grant 호출에서 드러난다
			assertThatThrownBy(() ->
					grantService.grant(command(userId, GrantSourceType.MISSION, duplicate.getId(), 1)))
					.isInstanceOf(DataIntegrityViolationException.class);
			status.setRollbackOnly();
		});
		// 새 트랜잭션에서 처음부터 다시 처리하면 사전 조회가 기존 청구를 찾아 기존 결과를 반환한다
		GrantResult retried = completeMission(parents, () -> { });
		assertThat(retried).isEqualTo(new GrantResult(existing.getLedgerId(), existing.getWalletId(), existing.getQuantity(),
				existing.getBalanceAfter(), existing.getGrantedAt(), existing.getExpiresAt(), true));
		assertThat(count("select count(*) from mission_reward_claims where user_id = ?", bytes(userId)))
				.isEqualTo(1);
		assertThat(ledgerCount(userId)).isEqualTo(1);
		assertThat(walletVersion(existing.getWalletId())).isEqualTo(1);
	}

	@Test
	@DisplayName("grant가 예외를 던지면 호출자 트랜잭션의 청구·지갑·원장·배분이 모두 롤백된다")
	void grantFailureRollsBackCallerTransaction() {
		// given
		TicketGrantSeeds.MissionParents first = seeds.missionParents(userId);
		TicketGrantSeeds.MissionParents second = seeds.missionParents(userId);
		// when
		// then
		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
			UUID granted = seeds.missionClaim(userId, first, 1);
			grantService.grant(command(userId, GrantSourceType.MISSION, granted, 1));
			// 청구는 2장인데 1장으로 요청해 불일치로 실패시킨다
			UUID mismatched = seeds.missionClaim(userId, second, 2);
			grantService.grant(command(userId, GrantSourceType.MISSION, mismatched, 1));
		}))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(TicketErrorCode.TICKET_GRANT_SOURCE_MISMATCH);
		assertThat(count("select count(*) from mission_reward_claims where user_id = ?", bytes(userId))).isZero();
		assertThat(walletCount(userId)).isZero();
		assertThat(ledgerCount(userId)).isZero();
		assertThat(allocationCount(userId)).isZero();
	}

	@Test
	@DisplayName("동시 완료로 청구 UNIQUE 위반이 나면 롤백 후 새 트랜잭션의 재처리가 기존 결과를 반환하고 지급은 한 번이다")
	void uniqueViolationRetryReturnsExistingGrant() throws Exception {
		// given
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		CountDownLatch firstGranted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<GrantResult> first = executor.submit(() -> completeMission(parents, () -> {
				firstGranted.countDown();
				await(releaseFirst);
			}));
			assertThat(firstGranted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

			// when: 두 번째 요청은 사전 조회에서 "없음"을 보고 청구를 넣다가 첫 트랜잭션의 청구 잠금에 막힌다
			Future<GrantResult> second = executor.submit(() -> completeMission(parents, () -> { }));
			lockWaits.awaitLockWaits(1);
			releaseFirst.countDown();
			GrantResult winner = first.get(WAIT_SECONDS, TimeUnit.SECONDS);

			// then: 두 번째 트랜잭션은 청구 UNIQUE 위반으로 전체 롤백된다
			assertThatThrownBy(() -> second.get(WAIT_SECONDS, TimeUnit.SECONDS))
					.isInstanceOf(ExecutionException.class)
					.hasCauseInstanceOf(DuplicateKeyException.class);

			// 새 트랜잭션에서 처음부터 다시 처리하면 사전 조회가 기존 청구를 찾아 기존 결과를 반환한다
			GrantResult retried = completeMission(parents, () -> { });
			assertThat(retried).isEqualTo(new GrantResult(winner.getLedgerId(), winner.getWalletId(), winner.getQuantity(),
					winner.getBalanceAfter(), winner.getGrantedAt(), winner.getExpiresAt(), true));
			assertThat(count("select count(*) from mission_reward_claims where user_id = ?", bytes(userId)))
					.isEqualTo(1);
			assertThat(ledgerCount(userId)).isEqualTo(1);
			assertThat(allocationCount(userId)).isEqualTo(1);
			assertThat(walletBalance(winner.getWalletId())).isEqualTo(1);
			assertThat(walletVersion(winner.getWalletId())).isEqualTo(1);
		} finally {
			releaseFirst.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("새 지갑의 ID는 UUID v7로 저장된다")
	void walletIdIsUuidV7() {
		// given
		// when
		GrantResult result = grantNewMissionClaim(userId, 1);
		// then
		UUID stored = uuid(jdbc.queryForObject("select id from ticket_wallets where user_id = ?", byte[].class,
				bytes(userId)));
		assertThat(stored).isEqualTo(result.getWalletId());
		assertThat(stored.version()).isEqualTo(7);
	}

	/**
	 * 설계 문서 §7의 호출자 흐름: 기존 청구 조회 → 있으면 findGrant, 없으면 청구 저장 후 grant.
	 * {@code beforeCommit}은 지급 뒤 커밋 전에 실행한다.
	 */
	private GrantResult completeMission(TicketGrantSeeds.MissionParents parents, Runnable beforeCommit) {
		return transaction.execute(status -> {
			Optional<UUID> existing = seeds.findMissionClaim(userId, parents.missionId());
			if (existing.isPresent()) {
				return grantService.findGrant(new GrantSource(GrantSourceType.MISSION, existing.get()))
						.orElseThrow();
			}
			UUID claimId = seeds.missionClaim(userId, parents, 1);
			GrantResult result = grantService.grant(command(userId, GrantSourceType.MISSION, claimId, 1));
			beforeCommit.run();
			return result;
		});
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
				throw new IllegalStateException("대기 시간이 지났다.");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
