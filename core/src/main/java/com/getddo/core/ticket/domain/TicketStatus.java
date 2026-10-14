package com.getddo.core.ticket.domain;

/**
 * 응모권 현재 상태. {@code tickets.status}와 {@code ticket_histories.status} ENUM과 같은 값을 가진다.
 *
 * <p>{@code AVAILABLE}은 최초 사용 가능, {@code RETURNED}는 반환되어 다시 사용 가능한 상태다.
 * 두 상태 모두 사용할 수 있으며 만료 시각이 지나지 않았을 때만 유효하다.</p>
 */
public enum TicketStatus {
	AVAILABLE,
	RETURNED,
	SPENT,
	EXPIRED;

	/** 응모에 사용할 수 있는 상태인지 알려 준다. 만료 시각은 별도로 확인해야 한다. */
	public boolean isUsable() {
		return this == AVAILABLE || this == RETURNED;
	}
}
