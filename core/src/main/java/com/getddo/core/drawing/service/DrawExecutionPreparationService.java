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
import com.getddo.core.drawing.domain.DrawExecutionState;
import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.domain.DrawSnapshotSource;
import com.getddo.core.drawing.repository.DrawExecutionRepository;
import com.getddo.core.drawing.repository.DrawResultRepository;
import com.getddo.core.drawing.repository.DrawSnapshotRepository;

import static com.getddo.core.drawing.exception.DrawingErrorCode.*;

/** 선정 전에 추첨 입력을 저장한다. 선정에 실패해도 같은 후보와 조건으로 다시 시도할 수 있게 한다. */
@Service
@RequiredArgsConstructor
public class DrawExecutionPreparationService {
	private final DrawResultRepository results;
	private final DrawSnapshotRepository snapshots;
	private final DrawExecutionRepository executions;
	private final List<DrawInputPreparation> preparations;
	private final TimeProvider time;

	/** 이벤트·실행 잠금과 유효 소유권을 확인한 뒤 실행 종류별 준비 기능으로 입력을 한 번 고정한다. 선정과 별도로 커밋하며 이미 고정된 입력은 재계산하지 않는다. */
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
