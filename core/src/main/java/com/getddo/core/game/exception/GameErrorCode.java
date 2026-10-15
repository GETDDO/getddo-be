package com.getddo.core.game.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.getddo.core.common.exception.ErrorCode;

/** 게임 시작(G03)의 업무 오류. 사용자 헤더 오류는 기존 공통 계약을 사용한다. */
@Getter
@RequiredArgsConstructor
public enum GameErrorCode implements ErrorCode {
	USER_INACTIVE(403, "GAME-001", "비활성 사용자는 게임을 시작할 수 없습니다."),
	GAME_NOT_FOUND(404, "GAME-002", "게임을 찾을 수 없습니다."),
	GAME_NOT_AVAILABLE(409, "GAME-003", "지금 시작할 수 없는 게임입니다.");

	private final int status;
	private final String code;
	private final String message;
}
