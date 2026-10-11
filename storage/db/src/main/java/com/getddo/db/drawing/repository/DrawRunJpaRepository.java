package com.getddo.db.drawing.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.getddo.core.drawing.domain.DrawType;
import com.getddo.db.drawing.entity.DrawRunEntity;

public interface DrawRunJpaRepository extends JpaRepository<DrawRunEntity, UUID> {
	Optional<DrawRunEntity> findByEventIdAndDrawTypeAndRunNumber(UUID eventId, DrawType drawType, int runNumber);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select run from DrawRunEntity run where run.id = :runId")
	Optional<DrawRunEntity> findForUpdate(@Param("runId") UUID runId);
}
