package com.getddo.db.ticket.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.db.ticket.entity.TicketHistoryEntity;

public interface TicketHistoryJpaRepository extends JpaRepository<TicketHistoryEntity, UUID> {

	List<TicketHistoryEntity> findByTicketIdInAndOperationType(Collection<UUID> ticketIds,
			TicketOperationType operationType);

	List<TicketHistoryEntity> findByEventEntryIdAndOperationTypeOrderByTicketId(UUID eventEntryId,
			TicketOperationType operationType);

	List<TicketHistoryEntity> findByOriginalUseHistoryIdInOrderByTicketId(Collection<UUID> originalUseHistoryIds);

	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select h from TicketHistoryEntity h where h.originalUseHistoryId in :ids order by h.ticketId")
	List<TicketHistoryEntity> lockByOriginalUseHistoryIdIn(@Param("ids") Collection<UUID> ids);
}
