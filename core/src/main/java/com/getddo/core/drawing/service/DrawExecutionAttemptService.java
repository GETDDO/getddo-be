package com.getddo.core.drawing.service;

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

/** 저장 입력을 사용한 선정·결과·상태를 원자적으로 확정한다. */
@Service
@RequiredArgsConstructor
public class DrawExecutionAttemptService {
	private final DrawResultRepository results;
	private final DrawSnapshotRepository snapshots;
	private final DrawExecutionRepository executions;
	private final DrawConfirmationService confirmation;
	private final TimeProvider time;

	@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
	public DrawExecutionResult execute(UUID runId, UUID attemptId) {
		UUID eventId = results.findEventId(runId).orElseThrow(() -> new BusinessException(INVALID_RUN));
		snapshots.lockEvent(eventId).orElseThrow(() -> new BusinessException(EVENT_NOT_FOUND));
		DrawExecutionState state = executions.lockRun(runId).orElseThrow(() -> new BusinessException(INVALID_RUN));
		if (state.isTerminal()) return stored(state);
		if (!attemptId.equals(state.getAttemptId()) || state.getLeaseUntil() == null
				|| !time.now().isBefore(state.getLeaseUntil())) throw new BusinessException(ATTEMPT_LOST);
		if (state.getFixedAt() == null) throw new BusinessException(INVALID_RUN);
		DrawConfirmation confirmed = confirmation.confirm(runId, attemptId);
		return new DrawExecutionResult(runId, DrawRunStatus.CONFIRMED, confirmed);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
	public DrawExecutionResult stored(DrawExecutionState state) {
		if (state.getStatus() == DrawRunStatus.CONFIRMED) {
			return new DrawExecutionResult(state.getRunId(), state.getStatus(), confirmation.confirm(state.getRunId()));
		}
		return new DrawExecutionResult(state.getRunId(), state.getStatus(), null);
	}
}
