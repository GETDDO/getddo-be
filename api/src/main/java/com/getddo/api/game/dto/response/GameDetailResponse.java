package com.getddo.api.game.dto.response;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;

import com.getddo.core.game.domain.Game;
import com.getddo.core.game.domain.GameDetailResult;

/** 사용자용 상세 정보. 내부 JSON 대신 게임별 공개 DTO만 반환한다. */
@Builder
public record GameDetailResponse(UUID id, String code, String name, String description, String ruleVersion,
		GameRulesResponse rules, Instant serverTime) {

	public static GameDetailResponse from(GameDetailResult result, Instant now) {
		Game game = result.getGame();
		return GameDetailResponse.builder().id(game.getId()).code(game.getCode()).name(game.getName())
				.description(game.getDescription()).ruleVersion(game.getRuleVersion())
				.rules(GameRulesResponse.from(result.getRules())).serverTime(now).build();
	}
}
