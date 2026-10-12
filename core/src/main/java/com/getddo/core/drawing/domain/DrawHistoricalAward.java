package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 이전 확정 실행이 생성한 불변 SELECTED 결과. 현재 취소·공개 여부는 선정 근거로 사용하지 않는다. */
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
}
