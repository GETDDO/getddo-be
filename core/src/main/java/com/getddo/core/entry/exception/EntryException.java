package com.getddo.core.entry.exception;

import com.getddo.core.common.exception.BusinessException;

public class EntryException extends BusinessException {

	public EntryException(EntryErrorCode errorCode) {
		super(errorCode);
	}
}
