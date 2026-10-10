package com.getddo.core.drawing.exception;

import com.getddo.core.common.exception.ErrorCode;

public enum DrawingErrorCode implements ErrorCode {
	EVENT_NOT_FOUND(404, "DRAW-001", "추첨 대상 이벤트를 찾을 수 없습니다."),
	NOT_EXECUTABLE(409, "DRAW-002", "추첨 준비가 가능한 시각 또는 상태가 아닙니다."),
	INVALID_EVIDENCE(409, "DRAW-003", "응모와 응모권 사용 근거가 일치하지 않습니다."),
	EXCLUSION_UNAVAILABLE(503, "DRAW-004", "추첨 제외 판단의 저장 근거가 아직 연결되지 않았습니다."),
	INVALID_RUN(409, "DRAW-005", "추첨 실행 상태 또는 스냅샷이 올바르지 않습니다.");

	private final int status;
	private final String code;
	private final String message;
	DrawingErrorCode(int status, String code, String message) {
		this.status = status; this.code = code; this.message = message;
	}
	@Override public int getStatus() { return status; }
	@Override public String getCode() { return code; }
	@Override public String getMessage() { return message; }
}
