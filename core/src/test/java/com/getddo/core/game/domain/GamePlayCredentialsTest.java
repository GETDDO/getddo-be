package com.getddo.core.game.domain;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GamePlayCredentialsTest {

	@Test
	@DisplayName("토큰 원문은 URL-safe 256-bit 값이고 저장 해시는 원문의 SHA-256이다")
	void issuesTokenAndHashSeparately() {
		// given
		SecureRandom random = mock(SecureRandom.class);
		doAnswer(invocation -> {
			Arrays.fill(invocation.getArgument(0, byte[].class), (byte) 1);
			return null;
		}).when(random).nextBytes(any(byte[].class));
		when(random.nextInt()).thenReturn(1);
		// when
		GamePlayCredentials issued = GamePlayCredentials.issue(random);
		// then
		assertThat(issued.getPlayToken()).isEqualTo("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE");
		assertThat(issued.getPlayTokenHash()).isEqualTo("56d5fa7333f6d747db42c239407e5da4c32f4c79f35d092b134fd35a402d9c5c");
		assertThat(issued.getPlayTokenHash()).isNotEqualTo(issued.getPlayToken());
		assertThat(issued.getGenerationSeed()).isEqualTo(1L);
	}

	@Test
	@DisplayName("seed 0은 다시 발급하고 signed -1은 unsigned 최대값으로 보존한다")
	void rejectsZeroSeedAndPreservesUnsignedMaximum() {
		// given
		SecureRandom random = mock(SecureRandom.class);
		when(random.nextInt()).thenReturn(0, -1);
		// when
		GamePlayCredentials issued = GamePlayCredentials.issue(random);
		// then
		assertThat(issued.getGenerationSeed()).isEqualTo(4294967295L);
	}

	@Test
	@DisplayName("토큰 해시가 맞아도 다른 사용자·게임·플레이에는 재사용할 수 없고 과거 플레이도 거절한다")
	void bindsTokenToStoredPlayOwnerAndGame() {
		// given
		SecureRandom random = mock(SecureRandom.class);
		when(random.nextInt()).thenReturn(1);
		GamePlayCredentials credentials = GamePlayCredentials.issue(random);
		UUID user = UUID.randomUUID();
		UUID game = UUID.randomUUID();
		GamePlay play = new GamePlay(UUID.randomUUID(), game, user, "color-word-v1", "{}",
				credentials.getPlayTokenHash(), "color-word-generator-v1", 1L, Instant.EPOCH, null, null, null);
		GamePlay otherPlay = new GamePlay(UUID.randomUUID(), game, user, "color-word-v1", "{}",
				"b".repeat(64), "color-word-generator-v1", 1L, Instant.EPOCH, null, null, null);
		GamePlay legacy = new GamePlay(UUID.randomUUID(), game, user, "v1", null, null, null, null,
				Instant.EPOCH, null, null, null);
		GamePlay notSaved = new GamePlay(null, game, user, "color-word-v1", "{}",
				credentials.getPlayTokenHash(), "color-word-generator-v1", 1L, null, null, null, null);
		// when / then
		assertThat(GamePlayCredentials.matches(play, user, game, credentials.getPlayToken())).isTrue();
		assertThat(GamePlayCredentials.matches(play, UUID.randomUUID(), game, credentials.getPlayToken())).isFalse();
		assertThat(GamePlayCredentials.matches(play, user, UUID.randomUUID(), credentials.getPlayToken())).isFalse();
		assertThat(GamePlayCredentials.matches(otherPlay, user, game, credentials.getPlayToken())).isFalse();
		assertThat(GamePlayCredentials.matches(play, user, game, "x".repeat(43))).isFalse();
		assertThat(GamePlayCredentials.matches(play, user, game, "invalid-token")).isFalse();
		assertThat(GamePlayCredentials.matches(play, user, game, null)).isFalse();
		assertThat(GamePlayCredentials.matches(legacy, user, game, credentials.getPlayToken())).isFalse();
		assertThat(GamePlayCredentials.matches(notSaved, user, game, credentials.getPlayToken())).isFalse();
	}

	@Test
	@DisplayName("암호학적 난수 공급자가 없으면 발급하지 않는다")
	void requiresRandomSource() {
		// given / when / then
		assertThatThrownBy(() -> GamePlayCredentials.issue(null)).isInstanceOf(NullPointerException.class);
	}
}
