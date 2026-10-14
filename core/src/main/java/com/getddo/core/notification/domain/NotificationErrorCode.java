package com.getddo.core.notification.domain;

import com.getddo.core.common.exception.ErrorCode;

/** 사용자 알림 조회·읽음 처리의 공개 오류 코드다. */
public enum NotificationErrorCode implements ErrorCode {
	/** 조회 크기나 커서가 올바르지 않은 경우. */
	INVALID_QUERY(400, "NOTIFICATION-001", "알림 조회 조건이 올바르지 않습니다."),
	/** 알림이 없거나 선택한 사용자에게 속하지 않은 경우. */
	NOT_FOUND(404, "NOTIFICATION-002", "알림을 찾을 수 없습니다.");

	private final int status;
	private final String code;
	private final String message;

	NotificationErrorCode(int status, String code, String message) {
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
