package com.getddo.db.ticket.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.ticket.domain.TicketLedgerAllocation;
import com.getddo.core.ticket.repository.TicketLedgerAllocationRepository;
import com.getddo.db.ticket.mapper.TicketLedgerMapper;

/**
 * 원장 배분 저장소 구현.
 *
 * <p>추가만 하는 행이므로 {@code JpaRepository.save}의 {@code merge} 대신 {@code persist}로 저장한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class TicketLedgerAllocationRepositoryImpl implements TicketLedgerAllocationRepository {

	private final EntityManager entityManager;
	private final TicketLedgerMapper ledgerMapper;

	@Override
	public void save(TicketLedgerAllocation allocation) {
		entityManager.persist(ledgerMapper.toEntity(allocation));
	}
}
