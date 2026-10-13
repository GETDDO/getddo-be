package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;

/** 저장된 추첨 자료를 표현하는 불변 조회 값이다. */
@Getter
public final class DrawRunView {
	private final UUID id;
	private final UUID eventId;
	private final int runNumber;
	private final DrawType drawType;
	private final DrawRunStatus status;
	private final UUID originalDrawId;
	private final String algorithmVersion;
	private final DrawSnapshot.Rules rules;
	private final DrawReplacementRulesView replacementRules;
	private final Instant snapshotFixedAt;
	private final Instant startedAt;
	private final Instant confirmedAt;
	private final Instant createdAt;

	public DrawRunView(UUID id, UUID eventId, int runNumber, DrawType drawType, DrawRunStatus status, UUID originalDrawId, String algorithmVersion, DrawSnapshot.Rules rules, Instant snapshotFixedAt, Instant startedAt, Instant confirmedAt, Instant createdAt) {
		this(id, eventId, runNumber, drawType, status, originalDrawId, algorithmVersion, rules,
				snapshotFixedAt, startedAt, confirmedAt, createdAt, null);
	}

	public DrawRunView(UUID id, UUID eventId, int runNumber, DrawType drawType, DrawRunStatus status,
			UUID originalDrawId, String algorithmVersion, DrawSnapshot.Rules rules, Instant snapshotFixedAt,
			Instant startedAt, Instant confirmedAt, Instant createdAt, DrawReplacementRulesView replacementRules) {
		this.id = id;
		this.eventId = eventId;
		this.runNumber = runNumber;
		this.drawType = drawType;
		this.status = status;
		this.originalDrawId = originalDrawId;
		this.algorithmVersion = algorithmVersion;
		this.rules = rules;
		this.replacementRules = replacementRules;
		this.snapshotFixedAt = snapshotFixedAt;
		this.startedAt = startedAt;
		this.confirmedAt = confirmedAt;
		this.createdAt = createdAt;
	}
}
