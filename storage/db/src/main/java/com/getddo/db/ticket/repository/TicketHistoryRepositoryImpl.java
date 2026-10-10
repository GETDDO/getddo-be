package com.getddo.db.ticket.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.ticket.domain.TicketHistory;
import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.core.ticket.repository.TicketHistoryRepository;
import com.getddo.db.ticket.mapper.TicketMapper;

@Repository
@RequiredArgsConstructor
public class TicketHistoryRepositoryImpl implements TicketHistoryRepository {

	private final TicketHistoryJpaRepository historyJpaRepository;
	private final TicketMapper ticketMapper;

	@Override
	public void saveAll(List<TicketHistory> histories) {
		historyJpaRepository.saveAll(histories.stream().map(ticketMapper::toEntity).toList());
	}

	@Override
	public List<TicketHistory> findUseHistories(UUID eventEntryId) {
		return historyJpaRepository
				.findByEventEntryIdAndOperationTypeOrderByTicketId(eventEntryId, TicketOperationType.USE).stream()
				.map(ticketMapper::toDomain).toList();
	}

	@Override
	public List<TicketHistory> findRefundsOf(Collection<UUID> originalUseHistoryIds) {
		return historyJpaRepository.findByOriginalUseHistoryIdInOrderByTicketId(originalUseHistoryIds).stream()
				.map(ticketMapper::toDomain).toList();
	}
}
