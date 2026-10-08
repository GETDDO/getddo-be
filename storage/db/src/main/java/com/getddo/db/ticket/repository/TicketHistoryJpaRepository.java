package com.getddo.db.ticket.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.db.ticket.entity.TicketHistoryEntity;

public interface TicketHistoryJpaRepository extends JpaRepository<TicketHistoryEntity, UUID> {

	List<TicketHistoryEntity> findByTicketIdInAndOperationType(Collection<UUID> ticketIds,
			TicketOperationType operationType);
}
