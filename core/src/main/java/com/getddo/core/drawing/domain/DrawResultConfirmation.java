package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;
import lombok.EqualsAndHashCode;

/** 실행 결과 확정과 구분되는 관리자 확인의 불변 저장값이다. */
@Getter
@EqualsAndHashCode
public final class DrawResultConfirmation {
	private final UUID id;
	private final UUID runId;
	private final UUID confirmedBy;
	private final Instant confirmedAt;

	/** 실행과 처리자·확인 시각의 필수값을 검증하고 관리자 확인 기록을 구성한다. */
	public DrawResultConfirmation(UUID id, UUID runId, UUID confirmedBy, Instant confirmedAt) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(runId, "runId");
		Objects.requireNonNull(confirmedBy, "confirmedBy");
		Objects.requireNonNull(confirmedAt, "confirmedAt");

		this.id = id;
		this.runId = runId;
		this.confirmedBy = confirmedBy;
		this.confirmedAt = confirmedAt;
	}
}
