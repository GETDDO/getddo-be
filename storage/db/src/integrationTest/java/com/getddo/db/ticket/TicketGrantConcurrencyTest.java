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

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.GrantSourceType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 응모권 지급이 동시에 일어나는 경우를 실제 MySQL 잠금으로 검증한다.
 *
 * <p>순서가 중요한 테스트는 앞 트랜잭션이 지급 후 커밋 전에 멈춘 상태에서 뒤 트랜잭션이 실제로 잠금 대기에
 * 들어간 것({@link LockWaitProbe})을 확인한 뒤 앞 트랜잭션을 풀어 준다.</p>
 */
class TicketGrantConcurrencyTest extends TicketIntegrationTestSupport {

	private static final long WAIT_SECONDS = 30;

	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("같은 사용자의 서로 다른 청구 여러 개를 동시에 지급해도 서로 기다리지 않고 모두 지급된다")
	void manyConcurrentGrantsAllSucceed() throws Exception {
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
			assertThat(results).allMatch(result -> !result.isReplayed() && result.getQuantity() == 1);
			assertThat(ticketCount(userId)).isEqualTo(requests);
			assertThat(historyCount(userId)).isEqualTo(requests);
		} finally {
			start.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	@Test
	@DisplayName("같은 청구를 동시에 지급하면 한쪽만 성공하고 다른 쪽은 UNIQUE 위반으로 롤백되어 지급은 한 번이다")
	void sameClaimGrantedOnce() throws Exception {
		// given
		UUID claim = committedGameClaim();
		// when: 두 트랜잭션 모두 기존 응모권 조회에서 아무것도 못 본 채 진행한다
		List<Object> outcomes = runWhileFirstHoldsLock(claim, claim);
		// then
		assertThat(outcomes.get(0)).isInstanceOf(GrantResult.class);
		assertThat(outcomes.get(1)).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(ticketCount(userId)).isEqualTo(1);
		assertThat(historyCount(userId)).isEqualTo(1);
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
				// INSERT는 flush 때 나간다. 커밋 전에 내보내 UNIQUE 항목 잠금을 쥔 채 멈춘다.
				entityManager.flush();
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
			ConcurrentTasks.shutdownAndAwait(executor);
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
