package com.getddo.core.drawing.domain;

import java.util.UUID;

import lombok.Getter;

/** 저장된 추첨 자료를 표현하는 불변 조회 값이다. */
@Getter
public final class DrawResultView {
	private final UUID id;
	private final UUID runId;
	private final UUID prizeId;
	private final UUID prizeEventId;
	private final int slotNumber;
	private final Integer selectionOrder;
	private final DrawSelectionResultType resultType;
	private final UUID candidateId;
	private final boolean wasPublished;
	private final boolean canceled;

	public DrawResultView(UUID id, UUID runId, UUID prizeId, UUID prizeEventId, int slotNumber, Integer selectionOrder, DrawSelectionResultType resultType, UUID candidateId, boolean wasPublished, boolean canceled) {
		this.id = id;
		this.runId = runId;
		this.prizeId = prizeId;
		this.prizeEventId = prizeEventId;
		this.slotNumber = slotNumber;
		this.selectionOrder = selectionOrder;
		this.resultType = resultType;
		this.candidateId = candidateId;
		this.wasPublished = wasPublished;
		this.canceled = canceled;
	}
}
