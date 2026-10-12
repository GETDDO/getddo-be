package com.getddo.core.game.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GamePlayTest {

	private static final UUID USER = UUID.randomUUID();
	private static final UUID GAME = UUID.randomUUID();
	private static final Instant START = Instant.parse("2026-10-12T14:59:59Z");
	private static final Instant END = Instant.parse("2026-10-12T15:00:00Z");
	private static final String HASH = "a".repeat(64);

	@Test
	@DisplayName("같은 입력 재시도는 최초 결과와 완료 시각을 유지하고 변경 입력은 거절한다")
	void preservesFirstResult() {
		// given
		GamePlay valid = started().complete(10L, HASH, END);
		// when
		GamePlay replay = valid.complete(999L, HASH, END.plusSeconds(86400));
		// then
		assertThat(replay).isSameAs(valid);
		assertThatThrownBy(() -> valid.complete(20L, "b".repeat(64), END))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("시작 발급은 0이 아닌 unsigned 32-bit seed와 토큰 해시를 요구한다")
	void validatesStartContext() {
		// given
		Game game = new Game(GAME, "COLOR_WORD", "색깔", null, "{}", "color-word-v1", true);
		// when
		GamePlay maximum = GamePlay.start(game, USER, HASH, "color-word-generator-v1", 4294967295L);
		// then
		assertThat(maximum.getGenerationSeed()).isEqualTo(4294967295L);
		assertThat(maximum.getId()).isNull();
		assertThatThrownBy(() -> GamePlay.start(game, USER, HASH, "v1", 0))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> GamePlay.start(game, USER, HASH, "v1", 4294967296L))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> GamePlay.start(game, USER, "raw-token", "v1", 1))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> GamePlay.start(new Game(GAME, "OFF", "중지", null, "{}", "v1", false),
				USER, HASH, "v1", 1)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("검증 기준 없는 과거 플레이·음수 점수·시작 전 완료는 확정할 수 없다")
	void rejectsUnverifiableResult() {
		// given
		GamePlay legacy = new GamePlay(UUID.randomUUID(), GAME, USER, "v1", null, null, null, null,
				START, null, null, null);
		// when
		// then
		assertThatThrownBy(() -> legacy.complete(10L, HASH, END)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> started().complete(-1L, HASH, END)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> started().complete(10L, HASH, START.minusSeconds(1)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("0점 정상 완료·무효 완료·미완료를 구분하고 완료 시각을 마이크로초로 보존한다")
	void distinguishesResultStates() {
		// given
		GamePlay unfinished = started();
		// when
		GamePlay zero = unfinished.complete(0L, HASH, END.plusNanos(123456789));
		GamePlay invalid = unfinished.complete(null, HASH, END);
		// then
		assertThat(unfinished.isCompleted()).isFalse();
		assertThat(unfinished.isValid()).isFalse();
		assertThat(zero.isCompleted()).isTrue();
		assertThat(zero.isValid()).isTrue();
		assertThat(zero.getScore()).isZero();
		assertThat(zero.getCompletedAt()).isEqualTo(END.plusNanos(123456000));
		assertThat(invalid.isCompleted()).isTrue();
		assertThat(invalid.isValid()).isFalse();
		assertThat(invalid.getScore()).isNull();
		assertThat(invalid.complete(10L, HASH, END.plusSeconds(1))).isSameAs(invalid);
	}

	private GamePlay started() {
		return new GamePlay(UUID.randomUUID(), GAME, USER, "color-word-v1", "{}", HASH,
				"color-word-generator-v1", 123456789L, START, null, null, null);
	}
}
