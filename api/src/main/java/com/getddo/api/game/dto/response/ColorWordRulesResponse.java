package com.getddo.api.game.dto.response;

import lombok.Builder;

import com.getddo.core.game.domain.ColorWordRules;

/** 색깔 게임 시작 응답의 공개 규칙. 저장된 JSON의 내부 필드는 전달하지 않는다. */
@Builder
public record ColorWordRulesResponse(int initialLives, int pointsPerCorrect, int initialTimeLimitMs,
		int timeLimitDecreaseMs, int minimumTimeLimitMs) {

	public static ColorWordRulesResponse from(ColorWordRules rules) {
		return ColorWordRulesResponse.builder().initialLives(rules.getInitialLives())
				.pointsPerCorrect(rules.getPointsPerCorrect()).initialTimeLimitMs(rules.getInitialTimeLimitMs())
				.timeLimitDecreaseMs(rules.getTimeLimitDecreaseMs()).minimumTimeLimitMs(rules.getMinimumTimeLimitMs())
				.build();
	}
}
