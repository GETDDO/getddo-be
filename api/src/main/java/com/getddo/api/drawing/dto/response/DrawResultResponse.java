package com.getddo.api.drawing.dto.response;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getddo.core.drawing.domain.DrawResultView;
import com.getddo.core.drawing.domain.DrawSelectionResultType;

/** 실제 공개 명단 포함 이력과 당첨 취소 여부를 독립적으로 제공한다. */
public record DrawResultResponse(UUID id, UUID prizeId, Integer prizeRank, int slotNumber, Integer selectionOrder,
		DrawSelectionResultType resultType, UUID candidateId, UUID userId, boolean wasPublished,
		@JsonProperty("isCanceled") boolean isCanceled) {
	public static DrawResultResponse from(DrawResultView result, Integer rank, UUID userId) {
		return new DrawResultResponse(result.getId(), result.getPrizeId(), rank, result.getSlotNumber(),
				result.getSelectionOrder(), result.getResultType(), result.getCandidateId(), userId,
				result.isWasPublished(), result.isCanceled());
	}
}
