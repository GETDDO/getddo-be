package com.getddo.core.drawing.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.getddo.core.common.exception.ErrorCode;

/** GD-137 전용 오류이며 실행 실패 코드 사전을 대신하지 않는다. */
@Getter
@RequiredArgsConstructor
public enum DrawInspectionErrorCode implements ErrorCode {
	ADMIN_REQUIRED(403, "DRAW-QUERY-001", "활성 관리자 권한이 필요합니다."),
	RUN_NOT_FOUND(404, "DRAW-QUERY-002", "추첨 실행을 찾을 수 없습니다."),
	CHECK_NOT_ALLOWED(409, "DRAW-QUERY-003", "확정된 추첨 실행만 검증할 수 있습니다.");
	private final int status;
	private final String code;
	private final String message;
}
