package com.getddo.api.game.dto.response;

import java.util.UUID;

import lombok.Builder;

import com.getddo.core.game.domain.Game;

/** 게임 선택에 필요한 식별자와 안내 정보. 시작 지원 여부나 내부 규칙을 의미하지 않는다. */
@Builder
public record GameSummaryResponse(UUID id, String code, String name, String description) {

	public static GameSummaryResponse from(Game game) {
		return GameSummaryResponse.builder().id(game.getId()).code(game.getCode()).name(game.getName())
				.description(game.getDescription()).build();
	}
}
