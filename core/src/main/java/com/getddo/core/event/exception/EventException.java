package com.getddo.core.event.exception;

import com.getddo.core.common.exception.BusinessException;

public class EventException extends BusinessException {

	public EventException(EventErrorCode errorCode) {
		super(errorCode);
	}

	public EventException(EventErrorCode errorCode, Throwable cause) {
		super(errorCode);
		initCause(cause);
	}
}
