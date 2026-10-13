package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/** 시도 ID를 실패 ID로 재사용하여 동일 실패 저장을 멱등 처리한다. */
@Getter
public final class DrawFailure {
	private final UUID id;
	private final UUID runId;
	private final String failureCode;
	private final Instant failedAt;
	private final String traceId;
	public DrawFailure(UUID id, UUID runId, String failureCode, Instant failedAt, String traceId) {
		this.id = Objects.requireNonNull(id);
		this.runId = Objects.requireNonNull(runId);
		if (failureCode == null || failureCode.isBlank() || failureCode.length() > 50
				|| (traceId != null && traceId.length() > 100)) throw new IllegalArgumentException("실패 기록 형식이 잘못됐습니다.");
		this.failureCode = failureCode;
		this.failedAt = Objects.requireNonNull(failedAt).truncatedTo(ChronoUnit.MICROS);
		this.traceId = traceId;
	}
}
