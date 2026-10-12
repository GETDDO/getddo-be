package com.getddo.db.drawing.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.drawing.entity.AwardCancellationEntity;

public interface AwardCancellationJpaRepository extends JpaRepository<AwardCancellationEntity, UUID> {
	Optional<AwardCancellationEntity> findByDrawResultId(UUID drawResultId);
	Optional<AwardCancellationEntity> findByRequestKey(UUID requestKey);
	List<AwardCancellationEntity> findByReplacementRunId(UUID runId);
}
