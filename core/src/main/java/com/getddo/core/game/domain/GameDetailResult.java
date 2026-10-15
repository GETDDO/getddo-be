package com.getddo.core.game.domain;

import java.util.Objects;

import lombok.Getter;

/** 조회한 게임 정보와 검증된 공개 규칙. 원본 규칙 JSON은 API DTO에서 노출하지 않는다. */
@Getter
public final class GameDetailResult {

	private final Game game;
	private final GameStartRules rules;

	public GameDetailResult(Game game, GameStartRules rules) {
		this.game = Objects.requireNonNull(game, "game");
		this.rules = Objects.requireNonNull(rules, "rules");
	}
}
