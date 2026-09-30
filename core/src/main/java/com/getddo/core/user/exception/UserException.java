package com.getddo.core.user.exception;

import com.getddo.core.common.exception.BusinessException;

public class UserException extends BusinessException {

	public UserException(UserErrorCode errorCode) {
		super(errorCode);
	}
}
