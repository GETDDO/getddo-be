package com.getddo.core.ticket.domain;

/** 반환할 때 잠가 읽은 응모권이 사용 이력과 어떤 관계인지 나타낸다. */
public enum RefundTicketState {
	/** 사용 직후 상태 그대로다. 반환할 수 있다. */
	SPENT_AS_USED,
	/** 이 사용을 되돌리는 반환이 이미 반영됐다. */
	ALREADY_REFUNDED,
	/** 다른 처리가 끼어들어 이 사용과 이어지지 않는다. */
	CHANGED
}
