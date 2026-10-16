package com.getddo.core.game.domain;

/** 처리 중은 응답 상태이며 DB의 확정 결과와 구분한다. */
public enum GameResultStatus {
	PROCESSING, VALID, INVALID
}
