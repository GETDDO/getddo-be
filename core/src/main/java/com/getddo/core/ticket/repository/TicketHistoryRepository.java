package com.getddo.core.ticket.repository;

import java.util.List;

import com.getddo.core.ticket.domain.TicketHistory;

/** 응모권 이력 저장소. 이력은 추가만 하며 수정·삭제하지 않는다. */
public interface TicketHistoryRepository {

	/**
	 * 새 이력을 추가한다.
	 *
	 * @param histories ID가 없는 이력
	 */
	void saveAll(List<TicketHistory> histories);
}
