package com.getddo.core.drawing.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.getddo.core.drawing.domain.DrawExecutionState;

/** 모든 변경은 서비스의 이벤트 → 실행 잠금 아래 같은 트랜잭션에서 수행한다. */
public interface DrawExecutionRepository {
	Optional<DrawExecutionState> lockRun(UUID runId);
	void claim(UUID runId, UUID attemptId, Instant now, Instant leaseUntil, Instant cycleStartedAt);
	void fail(UUID runId, Instant retryAt);
	void release(UUID runId);
	/** 등록 뒤 시작하지 못한 실행·만기 예약·만료 소유권을 조회한다. 운영자 대기는 제외한다. */
	List<UUID> findDue(Instant now, int limit);
}
