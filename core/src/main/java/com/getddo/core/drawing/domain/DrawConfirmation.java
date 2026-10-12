package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** 내부 확정 결과이며 사용자 공개 여부를 나타내지 않는다. */
@Getter
public final class DrawConfirmation {
	private final UUID runId;
	private final UUID eventId;
	private final Instant confirmedAt;
	private final List<DrawResult> results;

	/** 확정 실행의 식별자·시각과 결과 목록을 보존한다. 목록을 복사하여 호출자의 변경을 차단한다. */
	public DrawConfirmation(UUID runId, UUID eventId, Instant confirmedAt, List<DrawResult> results) {
		this.runId = Objects.requireNonNull(runId, "runId");
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.confirmedAt = Objects.requireNonNull(confirmedAt, "confirmedAt");
		this.results = List.copyOf(results);
	}
}
