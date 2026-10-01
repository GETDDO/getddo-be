package com.getddo.core.attendance.exception;

import com.getddo.core.common.exception.BusinessException;

public class AttendanceException extends BusinessException {

	public AttendanceException(AttendanceErrorCode errorCode) {
		super(errorCode);
	}
}
