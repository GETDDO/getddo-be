package com.getddo.db.drawing.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.core.drawing.domain.DrawType;
import com.getddo.db.drawing.entity.DrawRunEntity;

public interface DrawRunJpaRepository extends JpaRepository<DrawRunEntity, UUID> {
	Optional<DrawRunEntity> findByEventIdAndDrawTypeAndRunNumber(UUID eventId, DrawType drawType, int runNumber);
}
