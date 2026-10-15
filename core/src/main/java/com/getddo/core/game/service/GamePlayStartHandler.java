package com.getddo.core.game.service;

import com.getddo.core.game.domain.Game;
import com.getddo.core.game.domain.GameStartConfiguration;

/** 게임 코드·규칙 버전에 맞는 시작 규칙을 준비한다. 토큰 발급·저장은 공통 서비스가 맡는다. */
public interface GamePlayStartHandler {

	boolean supports(Game game);

	GameStartConfiguration prepare(Game game);
}
