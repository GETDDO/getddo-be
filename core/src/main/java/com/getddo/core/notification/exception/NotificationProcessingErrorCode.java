package com.getddo.core.notification.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.getddo.core.common.exception.ErrorCode;

/** 알림 작업의 입력·발생 건 충돌·처리 실패를 구분한다. */
@Getter
@RequiredArgsConstructor
public enum NotificationProcessingErrorCode implements ErrorCode {
	INVALID_JOB(400, "NOTIFICATION-003", "알림 작업 입력이 올바르지 않습니다."),
	OCCURRENCE_CONFLICT(409, "NOTIFICATION-004", "같은 발생 건의 알림 작업 입력이 다릅니다."),
	TEMPORARY_FAILURE(503, "NOTIFICATION-005", "알림 처리를 일시적으로 완료하지 못했습니다."),
	PROCESSING_FAILED(500, "NOTIFICATION-006", "알림 처리를 완료하지 못했습니다.");

	private final int status;
	private final String code;
	private final String message;
}
