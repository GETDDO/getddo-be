package com.getddo.db.ticket.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.ticket.entity.TicketEntity;

public interface TicketJpaRepository extends JpaRepository<TicketEntity, UUID> {

	List<TicketEntity> findByAttendanceRewardClaimId(UUID claimId);

	List<TicketEntity> findByMissionRewardClaimId(UUID claimId);

	List<TicketEntity> findByGameRewardClaimId(UUID claimId);
}
