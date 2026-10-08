package com.getddo.core.ticket.domain;

/**
 * 응모권 처리 유형. {@code ticket_histories.operation_type} ENUM과 같은 값을 가진다.
 *
 * <p>현재 구현하는 처리는 지급({@code GRANT})이다. 사용·반환·만료·정정은 해당 기능이 들어올 때 서비스가 추가한다.
 * 부정 획득분 회수는 제공하지 않는다.</p>
 */
public enum TicketOperationType {
	GRANT,
	USE,
	REFUND,
	EXPIRE,
	CORRECTION
}
