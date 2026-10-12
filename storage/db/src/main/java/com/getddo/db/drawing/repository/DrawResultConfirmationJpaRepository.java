package com.getddo.db.drawing.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import com.getddo.db.drawing.entity.DrawResultConfirmationEntity;

public interface DrawResultConfirmationJpaRepository extends JpaRepository<DrawResultConfirmationEntity, UUID> {
	Optional<DrawResultConfirmationEntity> findByRunId(UUID runId);
	List<DrawResultConfirmationEntity> findByRunIdIn(Set<UUID> runIds);
}
