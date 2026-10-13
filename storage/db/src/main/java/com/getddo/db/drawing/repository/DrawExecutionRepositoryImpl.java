package com.getddo.db.drawing.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.DrawExecutionState;
import com.getddo.core.drawing.repository.DrawExecutionRepository;
import com.getddo.db.drawing.entity.DrawRunEntity;
import static com.getddo.core.drawing.exception.DrawingErrorCode.INVALID_RUN;

/** 제어 상태는 기존 DrawRunEntity에만 저장한다. 업무 트랜잭션은 서비스 책임이다. */
@Repository
@RequiredArgsConstructor
public class DrawExecutionRepositoryImpl implements DrawExecutionRepository {
	private final DrawRunJpaRepository runs;
	@Override public Optional<DrawExecutionState> lockRun(UUID id) { return runs.findForUpdate(id).map(this::state); }
	private DrawExecutionState state(DrawRunEntity run) {
		return new DrawExecutionState(run.getId(), run.getEventId(), run.getDrawType(), run.getStatus(),
				run.getSnapshotFixedAt(), run.getAttemptId(), run.getLeaseUntil(), run.getRetryAt(), run.getRetryCycleStartedAt());
	}
	private DrawRunEntity run(UUID id) { return runs.findById(id).orElseThrow(() -> new BusinessException(INVALID_RUN)); }
	@Override public void claim(UUID id, UUID attemptId, Instant now, Instant until, Instant cycle) {
		run(id).claim(attemptId, now, until, cycle); runs.flush();
	}
	@Override public void fail(UUID id, Instant retryAt) { run(id).fail(retryAt); runs.flush(); }
	@Override public void release(UUID id) { run(id).releaseAttempt(); runs.flush(); }
	@Override public List<UUID> findDue(Instant now, int limit) {
		return runs.findDue(now, PageRequest.of(0, limit));
	}
}
