package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

import lombok.Getter;
import lombok.experimental.Accessors;

/** 공개 업무가 읽는 기존 REPLACEMENT 실행·확정 결과·관리자 확인의 조회값이다. 실행을 생성하지 않는다. */
@Getter
@Accessors(fluent = true)
public final class DrawPublicationRun {
	private final UUID runId;
	private final UUID eventId;
	private final int runNumber;
	private final DrawRunStatus status;
	private final Instant confirmedAt;
	private final List<DrawResult> results;
	private final DrawResultConfirmation confirmation;

	public DrawPublicationRun(UUID runId, UUID eventId, int runNumber, DrawRunStatus status,
			Instant confirmedAt, List<DrawResult> results, DrawResultConfirmation confirmation) {
		this.runId = Objects.requireNonNull(runId, "runId");
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		if (runNumber < 1) throw new IllegalArgumentException("replacement runNumber must be positive");
		this.runNumber = runNumber;
		this.status = Objects.requireNonNull(status, "status");
		if (confirmation != null && !runId.equals(confirmation.runId()))
			throw new IllegalArgumentException("confirmation belongs to another run");
		this.confirmedAt = confirmedAt;
		this.results = List.copyOf(results);
		this.confirmation = confirmation;
	}
}
