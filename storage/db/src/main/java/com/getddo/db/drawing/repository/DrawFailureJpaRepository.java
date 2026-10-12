package com.getddo.db.drawing.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.getddo.db.drawing.entity.DrawFailureEntity;

public interface DrawFailureJpaRepository extends JpaRepository<DrawFailureEntity, UUID> {
	long countByRunId(UUID runId);
	long countByRunIdAndFailedAtGreaterThanEqual(UUID runId, Instant since);
	Optional<DrawFailureEntity> findFirstByRunIdOrderByFailedAtDescIdDesc(UUID runId);
}
