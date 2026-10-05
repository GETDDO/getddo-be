package com.getddo.core.notification.exception;

import com.getddo.core.common.exception.BusinessException;

/** 내부 알림 작업의 실패를 관련 오류 코드와 함께 전달한다. */
public class NotificationProcessingException extends BusinessException {
	/** 비동기 알림 처리에서 사용할 안전한 업무 오류 코드를 전달한다. */
	public NotificationProcessingException(NotificationProcessingErrorCode errorCode) {
		super(errorCode);
	}
}
