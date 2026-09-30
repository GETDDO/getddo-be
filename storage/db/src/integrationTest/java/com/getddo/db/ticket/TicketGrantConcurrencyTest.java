package com.getddo.db.ticket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.GrantSourceType;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 같은 지갑을 여러 트랜잭션이 동시에 갱신하는 경우를 실제 MySQL 잠금으로 검증한다.
 *
 * <p>순서가 중요한 테스트는 앞 트랜잭션이 지급 후 커밋 전에 멈춘 상태에서 뒤 트랜잭션이 실제로 잠금 대기에
 * 들어간 것({@link LockWaitProbe})을 확인한 뒤 앞 트랜잭션을 풀어 준다.</p>
 */
class TicketGrantConcurrencyTest extends TicketIntegrationTestSupport {

	private static final long WAIT_SECONDS = 30;

	@Test
	@DisplayName("같은 사용자·같은 월의 다른 청구 두 개를 동시에 지급하면 한 지갑에 합산되고 wallet_version이 겹치지 않는다")
	void concurrentGrantsToExistingWallet() throws Exception {
		// given: 이미 지갑이 있다
		GrantResult existing = grantNewMissionClaim(userId, 1);
		UUID claimA = committedGameClaim();
		UUID claimB = committedGameClaim();
		// when
		List<Object> outcomes = runWhileFirstHoldsLock(claimA, claimB);
		// then
		assertThat(outcomes).allMatch(GrantResult.class::isInstance);
		assertThat(walletCount(userId)).isEqualTo(1);
		assertThat(walletBalance(existing.getWalletId())).isEqualTo(3);
		assertThat(walletVersion(existing.getWalletId())).isEqualTo(3);
		assertThat(ledgerVersions()).containsExactly(1L, 2L, 3L);
	}

	@Test
	@DisplayName("지갑이 없을 때 두 지급이 동시에 오면 지갑은 하나만 생기고 뒤 지급은 앞 지급의 커밋을 기다린다")
	void concurrentGrantsCreateSingleWallet() throws Exception {
		// given
		UUID claimA = committedGameClaim();
		UUID claimB = committedGameClaim();
		// when
		List<Object> outcomes = runWhileFirstHoldsLock(claimA, claimB);
		// then
		assertThat(outcomes).allMatch(GrantResult.class::isInstance);
		GrantResult first = (GrantResult) outcomes.get(0);
		GrantResult second = (GrantResult) outcomes.get(1);
		assertThat(second.getWalletId()).isEqualTo(first.getWalletId());
		assertThat(walletCount(userId)).isEqualTo(1);
		assertThat(walletBalance(first.getWalletId())).isEqualTo(2);
		assertThat(ledgerVersions()).containsExactly(1L, 2L);
	}

	@Test
	@DisplayName("지갑이 없을 때 여러 지급이 한꺼번에 와도 지갑 하나에 모두 반영되고 wallet_version이 1부터 빠짐없이 이어진다")
	void manyConcurrentGrantsCreateSingleWallet() throws Exception {
		// given
		int requests = 8;
		List<UUID> claims = new ArrayList<>();
		for (int i = 0; i < requests; i++) {
			claims.add(committedGameClaim());
		}
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(requests);
		try {
			List<Future<GrantResult>> futures = new ArrayList<>();
			for (UUID claim : claims) {
				futures.add(executor.submit(() -> {
					start.await();
					return transaction.execute(status ->
							grantService.grant(command(userId, GrantSourceType.GAME, claim, 1)));
				}));
			}
			// when
			start.countDown();
			List<GrantResult> results = new ArrayList<>();
			for (Future<GrantResult> future : futures) {
				results.add(future.get(WAIT_SECONDS, TimeUnit.SECONDS));
			}
			// then
			assertThat(results).extracting(GrantResult::getWalletId).containsOnly(results.get(0).getWalletId());
			assertThat(walletCount(userId)).isEqualTo(1);
			assertThat(walletBalance(results.get(0).getWalletId())).isEqualTo(requests);
			assertThat(ledgerVersions()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("같은 청구를 동시에 지급하면 한쪽만 성공하고 다른 쪽은 롤백되어 지급은 한 번이다")
	void sameClaimGrantedOnce() throws Exception {
		// given
		UUID claim = committedGameClaim();
		// when: 두 트랜잭션 모두 멱등 조회에서 기존 원장을 못 본 채 진행한다
		List<Object> outcomes = runWhileFirstHoldsLock(claim, claim);
		// then
		GrantResult winner = (GrantResult) outcomes.get(0);
		assertThat(outcomes.get(1)).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(ledgerCount(userId)).isEqualTo(1);
		assertThat(allocationCount(userId)).isEqualTo(1);
		assertThat(walletBalance(winner.getWalletId())).isEqualTo(1);
		assertThat(walletVersion(winner.getWalletId())).isEqualTo(1);
	}

	/**
	 * 첫 트랜잭션이 {@code firstClaim}을 지급하고 커밋 전에 멈춘 사이 둘째 트랜잭션이 {@code secondClaim} 지급을
	 * 시작해 잠금 대기에 들어가게 한 뒤, 첫 트랜잭션을 커밋시킨다.
	 *
	 * @return 각 트랜잭션의 결과. 성공이면 {@link GrantResult}, 실패면 원인 예외
	 */
	private List<Object> runWhileFirstHoldsLock(UUID firstClaim, UUID secondClaim) throws Exception {
		CountDownLatch firstGranted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<GrantResult> first = executor.submit(() -> transaction.execute(status -> {
				GrantResult result = grantService.grant(command(userId, GrantSourceType.GAME, firstClaim, 1));
				firstGranted.countDown();
				awaitQuietly(releaseFirst);
				return result;
			}));
			assertThat(firstGranted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			Future<GrantResult> second = executor.submit(() -> transaction.execute(status ->
					grantService.grant(command(userId, GrantSourceType.GAME, secondClaim, 1))));
			lockWaits.awaitLockWaits(1);
			releaseFirst.countDown();
			return List.of(outcome(first), outcome(second));
		} finally {
			releaseFirst.countDown();
			executor.shutdownNow();
		}
	}

	private static Object outcome(Future<GrantResult> future) throws Exception {
		try {
			return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			return e.getCause();
		}
	}

	private UUID committedGameClaim() {
		TicketGrantSeeds.GameParents game = seeds.gameParents(userId);
		return seeds.gameClaim(userId, game, 1);
	}

	private List<Long> ledgerVersions() {
		return jdbc.queryForList("select wallet_version from ticket_ledger where user_id = ? order by wallet_version",
				Long.class, bytes(userId));
	}

	private static void awaitQuietly(CountDownLatch latch) {
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
