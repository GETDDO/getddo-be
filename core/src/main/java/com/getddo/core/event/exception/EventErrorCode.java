package com.getddo.core.event.exception;

import com.getddo.core.common.exception.ErrorCode;

public enum EventErrorCode implements ErrorCode {
	INVALID_DETAILS(400, "EVENT-001", "이벤트 정보가 올바르지 않습니다."),
	INVALID_PERIOD(400, "EVENT-002", "이벤트 기간이 올바르지 않습니다."),
	INVALID_CONFIGURATION(400, "EVENT-003", "이벤트 응모 조건이 올바르지 않습니다."),
	INVALID_PRIZES(400, "EVENT-004", "경품 구성이 올바르지 않습니다."),
	USER_CONTEXT_REQUIRED(401, "EVENT-005", "등록된 사용자 정보가 필요합니다."),
	ADMIN_REQUIRED(403, "EVENT-006", "관리자만 이벤트를 등록할 수 있습니다.");

	private final int status;
	private final String code;
	private final String message;

	EventErrorCode(int status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}

	@Override
	public int getStatus() {
		return status;
	}

	@Override
	public String getCode() {
		return code;
	}

	@Override
	public String getMessage() {
		return message;
	}
}
