package com.getddo.db.drawing.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.getddo.db.drawing.entity.DrawFailureEntity;

public interface DrawFailureJpaRepository extends JpaRepository<DrawFailureEntity, UUID> {
	/** 운영자 재개 구간과 관계없이 실행의 전체 실패 이력 수를 조회한다. */
	long countByRunId(UUID runId);
	/** 지정 시각을 포함한 이후 실패 수를 조회해 복구 구간의 시도 한도를 계산한다. */
	long countByRunIdAndFailedAtGreaterThanEqual(UUID runId, Instant since);
	/** 실패 시각과 ID 역순으로 최근 실패를 안정적으로 조회한다. */
	Optional<DrawFailureEntity> findFirstByRunIdOrderByFailedAtDescIdDesc(UUID runId);
}
