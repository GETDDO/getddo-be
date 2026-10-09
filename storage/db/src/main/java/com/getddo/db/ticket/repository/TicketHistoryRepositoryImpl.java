package com.getddo.db.ticket.repository;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.ticket.domain.TicketHistory;
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
}
