package com.getddo.core.drawing.domain;

public enum DrawRunStatus {
	PREPARING, READY, RUNNING, CONFIRMED, FAILED, NO_ENTRIES,
	/** 과거 전원 제외 실행의 조회 호환용. 새 실행에는 사용하지 않는다. */
	NO_CANDIDATES
}
