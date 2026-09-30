package com.getddo.db.ticket.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.ticket.entity.TicketLedgerEntity;

public interface TicketLedgerJpaRepository extends JpaRepository<TicketLedgerEntity, UUID> {

	Optional<TicketLedgerEntity> findByIdempotencyKey(String idempotencyKey);
}
