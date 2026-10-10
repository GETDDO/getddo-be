package com.getddo.db.entry.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.getddo.db.entry.entity.EventParticipantEntity;

public interface EventParticipantJpaRepository extends JpaRepository<EventParticipantEntity, UUID> {

	Optional<EventParticipantEntity> findByEventIdAndUserId(UUID eventId, UUID userId);

	/** 응모자 ID만 잠그지 않고 읽는다. 없는 키를 범위로 잠그면 gap lock이 생기므로 ID로 PK 잠금을 따로 건다. */
	@Query("select p.id from EventParticipantEntity p where p.eventId = :eventId and p.userId = :userId")
	Optional<UUID> findIdByEventIdAndUserId(@Param("eventId") UUID eventId, @Param("userId") UUID userId);
}
