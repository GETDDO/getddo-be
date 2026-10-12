package com.getddo.core.drawing.service;

import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.drawing.domain.*;

/** 등록 서비스의 커밋 뒤 같은 실행 ID로 GD-87 실행·재개 기능을 호출한다. */
@Service
@RequiredArgsConstructor
public class AwardCancellationService {
	private final AwardCancellationRegistrationService registration;
	private final AwardCancellationQueryService query;
	private final DrawExecutionService executions;

	@Transactional(propagation = Propagation.NEVER)
	public AwardCancellationReceipt cancel(UUID actorId, UUID winId, UUID requestKey, String reason) {
		AwardCancellation cancellation;
		try {
			cancellation = registration.register(actorId, winId, requestKey, reason);
		} catch (DataIntegrityViolationException collision) {
			// 다른 이벤트의 동일 키가 먼저 커밋한 경우만 기존 처리를 반환한다.
			Optional<AwardCancellation> existing = registration.replay(actorId, winId, requestKey, reason);
			if (existing.isEmpty()) throw collision;
			cancellation = existing.get();
		}
		return new AwardCancellationReceipt(cancellation, executions.execute(cancellation.replacementRunId(), actorId));
	}

	@Transactional(propagation = Propagation.NEVER)
	public DrawExecutionResult startOrResume(UUID actorId, UUID cancellationId) {
		DrawSnapshot run = query.findConnected(actorId, cancellationId);
		return run.status() == DrawRunStatus.FAILED
				? executions.resume(run.runId(), actorId) : executions.execute(run.runId(), actorId);
	}
}
