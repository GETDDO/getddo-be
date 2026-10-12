package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 실행 결과 확정과 구분되는 관리자 확인의 불변 저장값이다. */
@Getter
@EqualsAndHashCode
@Accessors(fluent = true)
public final class DrawResultConfirmation {
	private final UUID id;
	private final UUID runId;
	private final UUID confirmedBy;
	private final Instant confirmedAt;

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
