package com.getddo.core.ticket.repository;

import com.getddo.core.ticket.domain.TicketLedgerAllocation;

/** 원장 배분 행 저장소. 배분 행은 추가만 한다. */
public interface TicketLedgerAllocationRepository {

	/**
	 * 새 배분 행을 추가한다.
	 *
	 * @param allocation 배분 행
	 */
	void save(TicketLedgerAllocation allocation);
}
