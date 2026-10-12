package com.getddo.core.drawing.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.getddo.core.drawing.domain.DrawFailure;

/** 이력의 집계·저장. 업무 트랜잭션 경계는 호출 서비스가 관리한다. */
public interface DrawFailureRepository {
	Optional<DrawFailure> findById(UUID failureId);
	void append(DrawFailure failure);
	long count(UUID runId);
	long countSince(UUID runId, Instant cycleStartedAt);
	Optional<DrawFailure> findLatest(UUID runId);
}
