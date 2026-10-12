package com.getddo.db.game;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import com.getddo.core.game.domain.GamePlay;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GamePlayRepositoryIntegrationTest extends GamePlayIntegrationTestSupport {

	@Autowired private EntityManager entityManager;

	@Test
	@DisplayName("판정 저장 뒤 트랜잭션이 실패하면 미완료 상태로 남고 다시 확정할 수 있다")
	void rollsBackCompletion() {
		// given
		GamePlay started = start();
		// when
		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
			plays.complete(started.complete(10L, HASH, time.now()));
			entityManager.flush();
			throw new IllegalStateException("후속 처리 실패");
		})).isInstanceOf(IllegalStateException.class).hasMessage("후속 처리 실패");
		// then
		GamePlay reloaded = plays.find(started.getId(), userId, game.getId()).orElseThrow();
		assertThat(reloaded.isCompleted()).isFalse();
		assertThat(reloaded.getScore()).isNull();
		assertThat(reloaded.getResultHash()).isNull();
		assertThat(complete(reloaded, 10L).isValid()).isTrue();
	}

	@Test
	@DisplayName("판정 저장은 호출자의 트랜잭션 없이는 실행하지 않는다")
	void requiresCallerTransaction() {
		// given
		GamePlay started = start();
		// when / then
		assertThatThrownBy(() -> plays.complete(started.complete(10L, HASH, time.now())))
				.isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
		assertThat(plays.find(started.getId(), userId, game.getId()).orElseThrow().isCompleted()).isFalse();
	}

	@Test
	@DisplayName("플레이에는 UUID v7·시작 시각·규칙 원본·최대 unsigned seed가 저장되고 소유자로 조회한다")
	void freezesVerificationContext() {
		// given
		GamePlay saved = start();
		// when
		jdbc.update("update games set rules = JSON_OBJECT('initialLives', 99), rule_version = 'v2' where id = ?",
				bytes(game.getId()));
		GamePlay reloaded = transaction.execute(status -> plays.find(saved.getId(), userId, game.getId()).orElseThrow());
		// then
		assertThat(reloaded).usingRecursiveComparison().isEqualTo(saved);
		assertThat(reloaded.getId().version()).isEqualTo(7);
		assertThat(reloaded.getStartedAt()).isEqualTo(time.now());
		assertThat(reloaded.getGenerationSeed()).isEqualTo(4294967295L);
		assertThat(reloaded.getRuleVersion()).isEqualTo("color-word-v1");
		assertThat(jdbc.queryForObject("select JSON_TYPE(rules_snapshot) from game_plays where id = ?",
				String.class, bytes(saved.getId()))).isEqualTo("OBJECT");
		assertThat(jdbc.queryForObject("select JSON_EXTRACT(rules_snapshot, '$.initialLives') from game_plays where id = ?",
				Integer.class, bytes(saved.getId()))).isEqualTo(3);
		assertThat(transaction.<Optional<GamePlay>>execute(status -> plays.find(saved.getId(), UUID.randomUUID(), game.getId()))).isEmpty();
		assertThat(transaction.<Optional<GamePlay>>execute(status -> plays.find(saved.getId(), userId, UUID.randomUUID()))).isEmpty();
	}

	@Test
	@DisplayName("같은 입력으로 저장을 재시도해도 최초 점수·완료 시각이 유지되며 다른 입력은 실패한다")
	void preservesFirstCompletion() {
		// given
		GamePlay started = start();
		GamePlay first = complete(started, 0L);
		clock.set(time.now().plusSeconds(86400));
		// when
		GamePlay replay = complete(started, 999L);
		// then
		assertThat(replay).usingRecursiveComparison().isEqualTo(first);
		assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
				plays.complete(started.complete(100L, "b".repeat(64), time.now()))))
				.hasRootCauseInstanceOf(IllegalStateException.class);
		assertThat(transaction.<Optional<GamePlay>>execute(status -> plays.find(first.getId(), userId, game.getId()))).get()
				.usingRecursiveComparison().isEqualTo(first);
	}

	@Test
	@DisplayName("무효 결과는 완료 시각과 입력 해시만 보존하고 미완료 기록과 구분한다")
	void persistsInvalidResult() {
		// given
		GamePlay started = start();
		// when
		complete(started, null);
		GamePlay reloaded = transaction.execute(status -> plays.find(started.getId(), userId, game.getId()).orElseThrow());
		// then
		assertThat(reloaded.isCompleted()).isTrue();
		assertThat(reloaded.isValid()).isFalse();
		assertThat(reloaded.getScore()).isNull();
		assertThat(reloaded.getResultHash()).isEqualTo(HASH);
	}

	@Test
	@DisplayName("과거 플레이는 읽되 새 판정에 사용하지 않고 검증 기준 일부만 저장하면 DB가 거절한다")
	void protectsLegacyAndIncompleteContext() {
		// given
		GamePlay legacy = transaction.execute(status -> plays.find(parents.playId(), userId, game.getId()).orElseThrow());
		// when
		// then
		assertThat(legacy.hasVerificationContext()).isFalse();
		assertThatThrownBy(() -> legacy.complete(10L, HASH, time.now())).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> jdbc.update("update game_plays set generation_seed = 1 where id = ?",
				bytes(legacy.getId()))).isInstanceOf(DataAccessException.class)
				.hasMessageContaining("chk_game_play_context");
		GamePlay current = start();
		assertThatThrownBy(() -> jdbc.update("update game_plays set generation_seed = 0 where id = ?",
				bytes(current.getId()))).isInstanceOf(DataAccessException.class)
				.hasMessageContaining("chk_game_play_context");
		assertThatThrownBy(() -> jdbc.update("update game_plays set score = 10 where id = ?",
				bytes(current.getId()))).isInstanceOf(DataAccessException.class)
				.hasMessageContaining("chk_game_play_result");
	}

}
