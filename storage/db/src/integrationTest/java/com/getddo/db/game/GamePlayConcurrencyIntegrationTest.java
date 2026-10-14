package com.getddo.db.game;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.game.domain.GamePlay;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(45)
class GamePlayConcurrencyIntegrationTest extends GamePlayIntegrationTestSupport {

	@Autowired private EntityManager entityManager;

	@Test
	@DisplayName("같은 플레이를 동시에 확정해도 행 잠금 후 최초 점수와 완료 시각만 남는다")
	void preservesFirstResultUnderConcurrentSubmission() throws Exception {
		// given
		GamePlay play = start();
		GamePlay submitted = play.complete(20L, RESULT_HASH, time.now().plusSeconds(1));
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<GamePlay> first = executor.submit(() -> transaction.execute(status -> {
				GamePlay current = plays.findForUpdate(play.getId(), userId, game.getId()).orElseThrow();
				GamePlay completed = plays.complete(current.complete(10L, RESULT_HASH, time.now()));
				entityManager.flush();
				locked.countDown();
				try {
					if (!release.await(20, TimeUnit.SECONDS)) {
						throw new AssertionError("테스트 트랜잭션 대기 시간이 초과되었습니다.");
					}
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new AssertionError(exception);
				}
				return completed;
			}));
			try {
				assertThat(locked.await(20, TimeUnit.SECONDS)).isTrue();
				// when
				Future<GamePlay> second = executor.submit(() -> transaction.execute(status -> plays.complete(submitted)));
				lockWaits.awaitLockWaits(1);
				release.countDown();
				GamePlay winner = first.get(20, TimeUnit.SECONDS);
				GamePlay replay = second.get(20, TimeUnit.SECONDS);
				// then
				assertThat(replay).usingRecursiveComparison().isEqualTo(winner);
				assertThat(replay.getScore()).isEqualTo(10);
				assertThat(plays.find(play.getId(), userId, game.getId()).orElseThrow())
						.usingRecursiveComparison().isEqualTo(winner);
			} finally {
				release.countDown();
			}
		}
	}
}
