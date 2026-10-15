package com.getddo.api.game.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.getddo.core.game.domain.ColorWordRules;
import com.getddo.core.game.domain.GameStartRules;

/** 게임별 공개 규칙 응답. 도메인 규칙 객체를 직접 직렬화하지 않는다. */
@Schema(oneOf = {ColorWordRulesResponse.class})
public sealed interface GameRulesResponse permits ColorWordRulesResponse {

	static GameRulesResponse from(GameStartRules rules) {
		if (rules instanceof ColorWordRules colorWordRules) {
			return ColorWordRulesResponse.from(colorWordRules);
		}
		throw new IllegalStateException("지원하지 않는 게임 시작 규칙 응답입니다.");
	}
}
