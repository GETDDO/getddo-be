package com.getddo.db.drawing.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.drawing.entity.DrawResultConfirmationEntity;

public interface DrawResultConfirmationJpaRepository extends JpaRepository<DrawResultConfirmationEntity, UUID> {
	/** 기존 실행에 저장된 관리자 확인 기록을 조회한다. */
	Optional<DrawResultConfirmationEntity> findByRunId(UUID runId);
	/** 여러 실행의 관리자 확인 기록을 일괄 조회한다. */
	List<DrawResultConfirmationEntity> findByRunIdIn(Set<UUID> runIds);
}
