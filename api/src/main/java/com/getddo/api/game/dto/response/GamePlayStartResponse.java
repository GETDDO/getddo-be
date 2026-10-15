package com.getddo.api.game.dto.response;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;

import com.getddo.core.game.domain.GamePlay;
import com.getddo.core.game.domain.GamePlayStartResult;

/** G03의 시작 정보. 토큰 해시·사용자 정보·결과 필드는 외부에 노출하지 않는다. */
@Builder
public record GamePlayStartResponse(UUID playId, String playToken, String ruleVersion, Instant startedAt,
		GameRulesResponse rules, GamePlayDataResponse gameData) {

	public static GamePlayStartResponse from(GamePlayStartResult result) {
		GamePlay play = result.getPlay();
		return GamePlayStartResponse.builder().playId(play.getId()).playToken(result.getPlayToken())
				.ruleVersion(play.getRuleVersion()).startedAt(play.getStartedAt())
				.rules(GameRulesResponse.from(result.getRules())).gameData(GamePlayDataResponse.from(play)).build();
	}
}
