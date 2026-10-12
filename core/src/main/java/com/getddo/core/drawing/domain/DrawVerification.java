package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Getter;

/** 검증자·시각과 항목별 결과를 보존한 검증 이력 한 건이다. */
@Getter
public final class DrawVerification {
	private final UUID id;
	private final UUID drawRunId;
	private final boolean passed;
	private final List<DrawCheckItem> checks;
	private final Instant verifiedAt;
	private final UUID verifiedBy;

	public DrawVerification(UUID id, UUID drawRunId, boolean passed, List<DrawCheckItem> checks, Instant verifiedAt, UUID verifiedBy) {
		this.id = id;
		this.drawRunId = drawRunId;
		this.passed = passed;
		this.checks = List.copyOf(checks);
		this.verifiedAt = verifiedAt;
		this.verifiedBy = verifiedBy;
	}
}
