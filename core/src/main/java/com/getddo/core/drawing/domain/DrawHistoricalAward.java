package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 이전 실행의 자리 결과와 취소 시각. 검증 입력 고정 당시의 최신 배정을 판단한다. */
@Getter
@RequiredArgsConstructor
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

	public DrawHistoricalAward(UUID id, UUID runId, UUID eventId, UUID prizeId, UUID prizeEventId,
			int slotNumber, UUID candidateId, UUID userId, Instant confirmedAt) {
		this(id, runId, eventId, prizeId, prizeEventId, slotNumber, candidateId, userId,
				confirmedAt, 0, null, DrawSelectionResultType.SELECTED);
	}

}
