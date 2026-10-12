package com.getddo.core.drawing.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.repository.*;
import static com.getddo.core.drawing.exception.DrawingErrorCode.*;

/** 입력 확정은 선정과 분리하여 선정 실패에도 저장 스냅샷을 보존한다. */
@Service
@RequiredArgsConstructor
public class DrawExecutionPreparationService {
	private final DrawResultRepository results;
	private final DrawSnapshotRepository snapshots;
	private final DrawExecutionRepository executions;
	private final List<DrawInputPreparation> preparations;
	private final TimeProvider time;

	@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
	public void prepare(UUID runId, UUID attemptId) {
		UUID eventId = results.findEventId(runId).orElseThrow(() -> new BusinessException(INVALID_RUN));
		DrawSnapshotSource.Event event = snapshots.lockEvent(eventId).orElseThrow(() -> new BusinessException(EVENT_NOT_FOUND));
		DrawExecutionState state = executions.lockRun(runId).orElseThrow(() -> new BusinessException(INVALID_RUN));
		if (state.isTerminal()) return;
		if (!attemptId.equals(state.getAttemptId()) || state.getLeaseUntil() == null
				|| !time.now().isBefore(state.getLeaseUntil())) throw new BusinessException(ATTEMPT_LOST);
		if (state.getFixedAt() != null) return;
		if (!DrawExecutionEligibility.isExecutable(event, state.getDrawType(), time.now())) {
			throw new BusinessException(NOT_EXECUTABLE);
		}
		List<DrawInputPreparation> matching = preparations.stream().filter(p -> p.supports(state.getDrawType())).toList();
		if (matching.size() != 1) throw new BusinessException(PREPARATION_UNAVAILABLE);
		DrawSnapshot input = matching.getFirst().prepare(runId, attemptId);
		if (!runId.equals(input.runId()) || !state.getEventId().equals(input.eventId())
				|| input.drawType() != state.getDrawType() || input.fixedAt() == null
				|| (input.status() != DrawRunStatus.READY && input.status() != DrawRunStatus.NO_ENTRIES)) {
			throw new BusinessException(INVALID_RUN);
		}
	}
}
