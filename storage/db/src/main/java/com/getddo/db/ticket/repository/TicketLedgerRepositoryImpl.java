package com.getddo.db.ticket.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.getddo.core.ticket.domain.TicketLedger;
import com.getddo.core.ticket.repository.TicketLedgerRepository;
import com.getddo.db.ticket.mapper.TicketLedgerMapper;

/** 응모권 원장 저장소 구현. */
@Repository
public class TicketLedgerRepositoryImpl implements TicketLedgerRepository {

	private final TicketLedgerJpaRepository ledgerJpaRepository;

	public TicketLedgerRepositoryImpl(TicketLedgerJpaRepository ledgerJpaRepository) {
		this.ledgerJpaRepository = ledgerJpaRepository;
	}

	@Override
	public Optional<TicketLedger> findByIdempotencyKey(String idempotencyKey) {
		return ledgerJpaRepository.findByIdempotencyKey(idempotencyKey).map(TicketLedgerMapper::toDomain);
	}

	@Override
	public TicketLedger save(TicketLedger ledger) {
		return TicketLedgerMapper.toDomain(ledgerJpaRepository.save(TicketLedgerMapper.toEntity(ledger)));
	}
}
