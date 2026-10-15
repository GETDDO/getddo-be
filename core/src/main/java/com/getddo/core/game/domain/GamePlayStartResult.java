package com.getddo.core.game.domain;

import java.util.Objects;

import lombok.Getter;

/** 저장된 플레이와 이번 시작 응답에만 사용할 토큰·공개 규칙을 묶는다. */
@Getter
public final class GamePlayStartResult {

	private final GamePlay play;
	private final String playToken;
	private final GameStartRules rules;

	public GamePlayStartResult(GamePlay play, String playToken, GameStartRules rules) {
		this.play = Objects.requireNonNull(play, "play");
		Objects.requireNonNull(play.getId(), "저장된 플레이 ID가 필요합니다.");
		Objects.requireNonNull(play.getStartedAt(), "저장된 시작 시각이 필요합니다.");
		this.playToken = Objects.requireNonNull(playToken, "playToken");
		this.rules = Objects.requireNonNull(rules, "rules");
	}
}
