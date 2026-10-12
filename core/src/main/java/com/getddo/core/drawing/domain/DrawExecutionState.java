package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** 기존 실행 행의 처리 상태. 실패 횟수는 이 모델에 중복 저장하지 않는다. */
@Getter
public final class DrawExecutionState {
	private final UUID runId;
	private final UUID eventId;
	private final DrawType drawType;
	private final DrawRunStatus status;
	private final Instant fixedAt;
	private final UUID attemptId;
	private final Instant leaseUntil;
	private final Instant retryAt;
	private final Instant retryCycleStartedAt;
	public DrawExecutionState(UUID runId, UUID eventId, DrawType drawType, DrawRunStatus status,
			Instant fixedAt, UUID attemptId, Instant leaseUntil, Instant retryAt, Instant retryCycleStartedAt) {
		this.runId = runId; this.eventId = eventId; this.drawType = drawType; this.status = status;
		this.fixedAt = fixedAt; this.attemptId = attemptId; this.leaseUntil = leaseUntil;
		this.retryAt = retryAt; this.retryCycleStartedAt = retryCycleStartedAt;
	}
	public boolean isTerminal() {
		return status == DrawRunStatus.CONFIRMED || status == DrawRunStatus.NO_ENTRIES || status == DrawRunStatus.NO_CANDIDATES;
	}
}
