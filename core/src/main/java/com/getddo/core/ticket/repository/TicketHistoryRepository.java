package com.getddo.core.ticket.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.getddo.core.ticket.domain.TicketHistory;

/** 응모권 이력 저장소. 이력은 추가만 하며 수정·삭제하지 않는다. */
public interface TicketHistoryRepository {

	/**
	 * 새 이력을 추가한다.
	 *
	 * @param histories ID가 없는 이력
	 */
	void saveAll(List<TicketHistory> histories);

	/** 응모의 사용 이력을 잠그지 않고 읽는다. */
	List<TicketHistory> findUseHistories(UUID eventEntryId);

	/** 주어진 사용 이력을 되돌리는 반환 이력을 잠그지 않고 읽는다. */
	List<TicketHistory> findRefundsOf(Collection<UUID> originalUseHistoryIds);
}
