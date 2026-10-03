package com.getddo.core.notification.exception;

import com.getddo.core.common.exception.BusinessException;

/** 내부 알림 작업의 실패를 관련 오류 코드와 함께 전달한다. */
public class NotificationProcessingException extends BusinessException {
	public NotificationProcessingException(NotificationProcessingErrorCode errorCode) {
		super(errorCode);
	}
}
