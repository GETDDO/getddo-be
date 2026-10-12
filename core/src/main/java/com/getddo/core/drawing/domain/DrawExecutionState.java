package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.Objects;
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
		this.runId = Objects.requireNonNull(runId, "runId");
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.drawType = Objects.requireNonNull(drawType, "drawType");
		this.status = Objects.requireNonNull(status, "status");
		if ((attemptId == null) != (leaseUntil == null)) {
			throw new IllegalArgumentException("시도 ID와 임대 시각은 함께 있어야 합니다.");
		}
		if (attemptId != null && (retryCycleStartedAt == null
				|| (status != DrawRunStatus.PREPARING && status != DrawRunStatus.READY && status != DrawRunStatus.RUNNING))) {
			throw new IllegalArgumentException("시도 소유권은 재시도 구간이 있는 준비·실행 상태에만 허용됩니다.");
		}
		if (retryAt != null && (status != DrawRunStatus.FAILED || attemptId != null || retryCycleStartedAt == null)) {
			throw new IllegalArgumentException("재시도 예약은 소유권 없는 실패 상태와 구간을 요구합니다.");
		}
		this.fixedAt = fixedAt; this.attemptId = attemptId; this.leaseUntil = leaseUntil;
		this.retryAt = retryAt; this.retryCycleStartedAt = retryCycleStartedAt;
	}
	public boolean isTerminal() {
		return status == DrawRunStatus.CONFIRMED || status == DrawRunStatus.NO_ENTRIES || status == DrawRunStatus.NO_CANDIDATES;
	}
}
