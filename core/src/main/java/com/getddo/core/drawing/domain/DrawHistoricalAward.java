package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import java.util.Objects;

/** 이전 실행의 자리 결과와 취소 시각. 검증 입력 고정 당시의 최신 배정을 판단한다. */
@Getter
public final class DrawHistoricalAward {
	private final UUID id;
	private final UUID runId;
	private final UUID eventId;
	private final UUID prizeId;
	private final UUID prizeEventId;
	private final int slotNumber;
	private final UUID candidateId;
	private final UUID userId;
	private final Instant confirmedAt;
	private final int runNumber;
	private final Instant canceledAt;
	private final DrawSelectionResultType resultType;

	/** 복원 시 회차와 결과 종류를 명시해 기본값에 의한 이력 혼동을 방지한다. */
	public DrawHistoricalAward(UUID id, UUID runId, UUID eventId, UUID prizeId, UUID prizeEventId,
			int slotNumber, UUID candidateId, UUID userId, Instant confirmedAt, int runNumber,
			Instant canceledAt, DrawSelectionResultType resultType) {
		this.id = id;
		this.runId = runId;
		this.eventId = eventId;
		this.prizeId = prizeId;
		this.prizeEventId = prizeEventId;
		this.slotNumber = slotNumber;
		this.candidateId = candidateId;
		this.userId = userId;
		this.confirmedAt = confirmedAt;
		this.runNumber = runNumber;
		this.canceledAt = canceledAt;
		this.resultType = Objects.requireNonNull(resultType, "resultType");
	}
}
