package com.getddo.core.drawing.service;

import java.sql.SQLTransientException;
import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.*;
import static com.getddo.core.drawing.exception.DrawingErrorCode.*;

/** 등록된 실행만 처리한다. 취소·새 재추첨·공개 기능을 만들지 않는다. */
@Service
@RequiredArgsConstructor
public class DrawExecutionService {
	private final DrawExecutionControlService control;
	private final DrawExecutionAttemptService attempts;
	private final DrawExecutionPreparationService preparations;
	private final TimeProvider time;

	/** 등록 커밋 후 호출한다. 대기·예약 상태를 일반 호출로 우회하지 않는다. */
	@Transactional(propagation = Propagation.NEVER)
	public DrawExecutionResult execute(UUID runId) { return executeClaim(control.claim(runId, null, false, false)); }
	/** GD-138의 관리자 시작은 실제 처리자를 전달한다. 운영자 대기 해제는 resume만 허용한다. */
	@Transactional(propagation = Propagation.NEVER)
	public DrawExecutionResult execute(UUID runId, UUID actorId) {
		if (actorId == null) throw new BusinessException(ADMIN_REQUIRED);
		return executeClaim(control.claim(runId, actorId, false, false));
	}
	/** GD-138도 같은 실행 재개를 사용한다. 실제 처리자를 권한·감사 기록에 전달한다. */
	@Transactional(propagation = Propagation.NEVER)
	public DrawExecutionResult resume(UUID runId, UUID actorId) { return executeClaim(control.claim(runId, actorId, true, false)); }
	/** 승인된 최초 추첨 HTTP 진입점은 REPLACEMENT를 허용하지 않는다. */
	@Transactional(propagation = Propagation.NEVER)
	public DrawExecutionResult resumeInitial(UUID runId, UUID actorId) { return executeClaim(control.claim(runId, actorId, true, true)); }

	/** 소유권을 얻은 호출만 입력 준비와 확정을 진행한다. 준비·선정 롤백 뒤 같은 시도의 실패를 별도 기록하며 복구 거절은 커밋 이후 전달한다. */
	private DrawExecutionResult executeClaim(DrawExecutionClaim claim) {
		if (claim.getRejectionCode() != null) throw new BusinessException(claim.getRejectionCode());
		DrawExecutionState state = claim.getState();
		if (!claim.isClaimed()) return attempts.stored(state);
		try {
			preparations.prepare(state.getRunId(), state.getAttemptId());
			return attempts.execute(state.getRunId(), state.getAttemptId());
		} catch (RuntimeException failure) {
			Instant failedAt = time.now();
			DrawExecutionState failed = control.recordFailure(state.getRunId(), state.getAttemptId(), classify(failure), failedAt);
			return attempts.stored(failed);
		}
	}

	/** 실제 일시적 저장 오류만 자동 재시도하며 미분류 오류는 운영자에게 맡긴다. */
	static DrawFailureCode classify(RuntimeException failure) {
		if (failure instanceof BusinessException business) {
			if (business.getErrorCode() == ATTEMPT_LOST) return DrawFailureCode.INTERRUPTED;
			if (business.getErrorCode() == INVALID_EVIDENCE) return DrawFailureCode.INVALID_EVIDENCE;
			if (business.getErrorCode() == INVALID_RUN) return DrawFailureCode.INVALID_RUN;
			if (business.getErrorCode() == NOT_EXECUTABLE) return DrawFailureCode.NOT_EXECUTABLE;
			return DrawFailureCode.INTERNAL_ERROR;
		}
		Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		Throwable current = failure;
		while (current != null && visited.add(current)) {
			if (current instanceof TransientDataAccessException || current instanceof SQLTransientException) return DrawFailureCode.TRANSIENT_STORAGE;
			current = current.getCause();
		}
		return DrawFailureCode.INTERNAL_ERROR;
	}
}
