package com.getddo.core.notification.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.getddo.core.common.exception.ErrorCode;

/** 관리자 알림의 조회 조건·권한·부재·복구 충돌을 구분한다. */
@Getter
@RequiredArgsConstructor
public enum NotificationAdminErrorCode implements ErrorCode {
	INVALID_QUERY(400, "NOTIFICATION-007", "알림 조회 조건이 올바르지 않습니다."),
	ADMIN_REQUIRED(403, "NOTIFICATION-008", "활성 관리자만 알림 운영 기능을 사용할 수 있습니다."),
	NOT_FOUND(404, "NOTIFICATION-009", "알림 작업 또는 알림을 찾을 수 없습니다."),
	RETRY_NOT_ALLOWED(409, "NOTIFICATION-010", "현재 상태에서는 알림 복구를 요청할 수 없습니다.");

	private final int status;
	private final String code;
	private final String message;
}
