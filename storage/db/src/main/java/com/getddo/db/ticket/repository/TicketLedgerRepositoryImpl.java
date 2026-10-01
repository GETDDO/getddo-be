package com.getddo.db.ticket.repository;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.ticket.domain.TicketLedger;
import com.getddo.core.ticket.repository.TicketLedgerRepository;
import com.getddo.db.ticket.mapper.TicketLedgerMapper;

@Repository
@RequiredArgsConstructor
public class TicketLedgerRepositoryImpl implements TicketLedgerRepository {

	private final TicketLedgerJpaRepository ledgerJpaRepository;
	private final TicketLedgerMapper ledgerMapper;

	@Override
	public Optional<TicketLedger> findByIdempotencyKey(String idempotencyKey) {
		return ledgerJpaRepository.findByIdempotencyKey(idempotencyKey).map(ledgerMapper::toDomain);
	}

	@Override
	public TicketLedger save(TicketLedger ledger) {
		return ledgerMapper.toDomain(ledgerJpaRepository.save(ledgerMapper.toEntity(ledger)));
	}
}
