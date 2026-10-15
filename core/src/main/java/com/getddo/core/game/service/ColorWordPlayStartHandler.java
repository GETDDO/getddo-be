package com.getddo.core.game.service;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

import com.getddo.core.game.domain.ColorWordRules;
import com.getddo.core.game.domain.Game;
import com.getddo.core.game.domain.GameStartConfiguration;

/** 색깔 게임의 지원 버전 확인과 시작 규칙 검증. */
@Component
public class ColorWordPlayStartHandler implements GamePlayStartHandler {

	@Override
	public boolean supports(Game game) {
		return ColorWordRules.GAME_CODE.equals(game.getCode())
				&& ColorWordRules.RULE_VERSION.equals(game.getRuleVersion());
	}

	@Override
	public GameStartConfiguration prepare(Game game) {
		try {
			return new GameStartConfiguration(ColorWordRules.fromSnapshot(game.getRules()),
					ColorWordRules.GENERATOR_VERSION);
		} catch (JacksonException | IllegalArgumentException invalidConfiguration) {
			// 예외 종류와 발생 위치만 보존하고 원본 메시지·JSON·원인 체인은 로그에 노출하지 않는다.
			IllegalStateException safeCause = new IllegalStateException(invalidConfiguration.getClass().getName());
			safeCause.setStackTrace(invalidConfiguration.getStackTrace());
			throw new IllegalStateException("저장된 색깔 게임 규칙이 올바르지 않습니다.", safeCause);
		}
	}
}
