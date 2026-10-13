package com.getddo.db.game.entity;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GamePlayEntityTest {

	@Test
	@DisplayName("빌더 생성 경로도 필수 게임·사용자·규칙 버전의 null을 거절한다")
	void rejectsMissingRequiredFields() {
		// given
		UUID gameId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		// when / then
		assertThatThrownBy(() -> GamePlayEntity.builder().userId(userId).ruleVersion("v1").build())
				.isInstanceOf(NullPointerException.class).hasMessage("gameId");
		assertThatThrownBy(() -> GamePlayEntity.builder().gameId(gameId).ruleVersion("v1").build())
				.isInstanceOf(NullPointerException.class).hasMessage("userId");
		assertThatThrownBy(() -> GamePlayEntity.builder().gameId(gameId).userId(userId).build())
				.isInstanceOf(NullPointerException.class).hasMessage("ruleVersion");
	}

	@Test
	@DisplayName("필수값을 갖춘 과거 행은 null 검증 기준으로 생성할 수 있다")
	void preservesNullableLegacyContext() {
		// given
		UUID gameId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		// when
		GamePlayEntity entity = GamePlayEntity.builder().gameId(gameId).userId(userId).ruleVersion("v1").build();
		// then
		assertThat(entity.getGameId()).isEqualTo(gameId);
		assertThat(entity.getUserId()).isEqualTo(userId);
		assertThat(entity.getRuleVersion()).isEqualTo("v1");
		assertThat(entity.getRulesSnapshot()).isNull();
	}
}
