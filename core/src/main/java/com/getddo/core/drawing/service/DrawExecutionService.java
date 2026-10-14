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

/** 등록된 추첨의 입력 준비와 결과 확정을 차례로 호출한다. 실패하면 기록을 남기고 저장된 실행 상태를 반환한다. */
@Service
@RequiredArgsConstructor
public class DrawExecutionService {
	private final DrawExecutionControlService control;
	private final DrawExecutionAttemptService attempts;
	private final DrawExecutionPreparationService preparations;
	private final TimeProvider time;

	/** 실행 등록이 저장된 뒤 호출한다. 재시도 예약과 관리자 확인 대기는 그대로 따른다. */
	@Transactional(propagation = Propagation.NEVER)
	public DrawExecutionResult execute(UUID runId) {
		return executeClaim(control.claim(runId, null, false, false));
	}

	/** 관리자의 실행 요청에 처리자 정보를 함께 전달한다. 관리자 확인 대기를 해제하려면 재개 요청을 사용한다. */
	@Transactional(propagation = Propagation.NEVER)
	public DrawExecutionResult execute(UUID runId, UUID actorId) {
		if (actorId == null) throw new BusinessException(ADMIN_REQUIRED);
		return executeClaim(control.claim(runId, actorId, false, false));
	}

	/** 관리자가 실패한 동일 실행을 재개한다. 처리자 정보를 권한 검사와 감사 기록에 사용한다. */
	@Transactional(propagation = Propagation.NEVER)
	public DrawExecutionResult resume(UUID runId, UUID actorId) {
		return executeClaim(control.claim(runId, actorId, true, false));
	}

	/** 최초 추첨의 실패만 재개한다. 재추첨 실행은 이 진입점에서 받지 않는다. */
	@Transactional(propagation = Propagation.NEVER)
	public DrawExecutionResult resumeInitial(UUID runId, UUID actorId) {
		return executeClaim(control.claim(runId, actorId, true, true));
	}

	/** 실행 권한을 얻은 요청만 준비와 선정을 진행한다. 실패한 작업은 롤백하고 같은 시도의 실패 기록은 별도로 저장한다. */
	private DrawExecutionResult executeClaim(DrawExecutionControlService.Claim claim) {
		if (claim.getRejectionCode() != null) throw new BusinessException(claim.getRejectionCode());
		DrawExecutionState state = claim.getState();
		if (!claim.isClaimed()) return attempts.stored(state);
		try {
			preparations.prepare(state.getRunId(), state.getAttemptId());
			return attempts.execute(state.getRunId(), state.getAttemptId());
		} catch (RuntimeException failure) {
			Instant failedAt = time.now();
			DrawExecutionState failed = control.recordFailure(state.getRunId(), state.getAttemptId(),
					classify(failure), failedAt);
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
