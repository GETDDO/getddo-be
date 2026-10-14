package com.getddo.core.ticket.exception;

import com.getddo.core.common.exception.BusinessException;

public class TicketException extends BusinessException {

	public TicketException(TicketErrorCode errorCode) {
		super(errorCode);
	}
}
