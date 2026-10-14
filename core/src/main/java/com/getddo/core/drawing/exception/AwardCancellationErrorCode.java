package com.getddo.core.drawing.exception;

import com.getddo.core.common.exception.ErrorCode;

/** 승인된 AD05·AD09의 취소·멱등 오류. 공통 실행 오류는 GD-87 코드를 사용한다. */
public enum AwardCancellationErrorCode implements ErrorCode {
	NOT_FOUND(404, "CANCEL-001", "당첨 또는 취소 기록을 찾을 수 없습니다."),
	NOT_CANCELABLE(409, "CANCEL-002", "현재 당첨을 취소할 수 없습니다."),
	KEY_CONFLICT(409, "CANCEL-003", "멱등 키가 다른 취소 요청에 사용되었습니다."),
	INVALID_REQUEST(400, "CANCEL-004", "취소 대상·멱등 키와 1~1000자의 사유가 필요합니다.");
	private final int status;
	private final String code;
	private final String message;
	AwardCancellationErrorCode(int status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}
	@Override public int getStatus() {
		return status;
	}
	@Override public String getCode() {
		return code;
	}
	@Override public String getMessage() {
		return message;
	}
}
