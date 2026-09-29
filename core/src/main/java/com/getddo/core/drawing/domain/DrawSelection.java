package com.getddo.core.drawing.domain;

import java.util.Objects;
import java.util.UUID;

/** 경품의 한 자리에서 확정할 선정 결과 또는 후보 부족으로 인한 미선정 결과다. */
public record DrawSelection(
		UUID prizeId,
		int rank,
		int slotNumber,
		Integer selectionOrder,
		UUID candidateId,
		UUID userId,
		ResultType resultType
) {

	public enum ResultType {
		SELECTED, UNFILLED
	}

	public DrawSelection {
		Objects.requireNonNull(prizeId, "prizeId");
		Objects.requireNonNull(resultType, "resultType");
		if (rank <= 0 || slotNumber <= 0) {
			throw new IllegalArgumentException("rank and slotNumber must be positive");
		}
		if (resultType == ResultType.SELECTED) {
			if (selectionOrder == null || selectionOrder <= 0 || candidateId == null || userId == null) {
				throw new IllegalArgumentException("selected result requires order and candidate");
			}
		} else if (selectionOrder != null || candidateId != null || userId != null) {
			throw new IllegalArgumentException("unfilled result cannot have a candidate or order");
		}
	}

	public static DrawSelection selected(DrawPrize prize, int slotNumber, int selectionOrder, DrawCandidate candidate) {
		Objects.requireNonNull(prize, "prize");
		Objects.requireNonNull(candidate, "candidate");
		return new DrawSelection(prize.prizeId(), prize.rank(), slotNumber, selectionOrder,
				candidate.candidateId(), candidate.userId(), ResultType.SELECTED);
	}

	public static DrawSelection unfilled(DrawPrize prize, int slotNumber) {
		Objects.requireNonNull(prize, "prize");
		return new DrawSelection(prize.prizeId(), prize.rank(), slotNumber, null, null, null, ResultType.UNFILLED);
	}
}
