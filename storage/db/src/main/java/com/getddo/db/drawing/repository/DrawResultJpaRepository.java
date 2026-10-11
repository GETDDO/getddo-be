package com.getddo.db.drawing.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.drawing.entity.DrawResultEntity;

public interface DrawResultJpaRepository extends JpaRepository<DrawResultEntity, UUID> {
	List<DrawResultEntity> findByDrawRunId(UUID runId);
	boolean existsByDrawRunId(UUID runId);
}
