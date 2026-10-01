package com.getddo.core.drawing.domain;

import java.util.Objects;
import java.util.UUID;

/** 경품의 한 자리에서 확정할 선정 결과 또는 후보 부족으로 인한 미선정 결과다. */
public final class DrawSelection {

	private final UUID prizeId;
	private final int rank;
	private final int slotNumber;
	private final Integer selectionOrder;
	private final UUID candidateId;
	private final UUID userId;
	private final DrawSelectionResultType resultType;

	public DrawSelection(UUID prizeId, int rank, int slotNumber, Integer selectionOrder,
			UUID candidateId, UUID userId, DrawSelectionResultType resultType) {
		this.prizeId = Objects.requireNonNull(prizeId, "prizeId");
		this.resultType = Objects.requireNonNull(resultType, "resultType");
		if (rank <= 0 || slotNumber <= 0) {
			throw new IllegalArgumentException("rank and slotNumber must be positive");
		}
		if (resultType == DrawSelectionResultType.SELECTED) {
			if (selectionOrder == null || selectionOrder <= 0 || candidateId == null || userId == null) {
				throw new IllegalArgumentException("selected result requires order and candidate");
			}
		} else if (selectionOrder != null || candidateId != null || userId != null) {
			throw new IllegalArgumentException("unfilled result cannot have a candidate or order");
		}
		this.rank = rank;
		this.slotNumber = slotNumber;
		this.selectionOrder = selectionOrder;
		this.candidateId = candidateId;
		this.userId = userId;
	}

	public static DrawSelection selected(DrawPrize prize, int slotNumber, int selectionOrder, DrawCandidate candidate) {
		Objects.requireNonNull(prize, "prize");
		Objects.requireNonNull(candidate, "candidate");
		return new DrawSelection(prize.getPrizeId(), prize.getRank(), slotNumber, selectionOrder,
				candidate.getCandidateId(), candidate.getUserId(), DrawSelectionResultType.SELECTED);
	}

	public static DrawSelection unfilled(DrawPrize prize, int slotNumber) {
		Objects.requireNonNull(prize, "prize");
		return new DrawSelection(prize.getPrizeId(), prize.getRank(), slotNumber, null, null, null, DrawSelectionResultType.UNFILLED);
	}

	public UUID getPrizeId() {
		return prizeId;
	}

	public int getRank() {
		return rank;
	}

	public int getSlotNumber() {
		return slotNumber;
	}

	public Integer getSelectionOrder() {
		return selectionOrder;
	}

	public UUID getCandidateId() {
		return candidateId;
	}

	public UUID getUserId() {
		return userId;
	}

	public DrawSelectionResultType getResultType() {
		return resultType;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof DrawSelection that)) {
			return false;
		}
		return rank == that.rank
				&& slotNumber == that.slotNumber
				&& prizeId.equals(that.prizeId)
				&& Objects.equals(selectionOrder, that.selectionOrder)
				&& Objects.equals(candidateId, that.candidateId)
				&& Objects.equals(userId, that.userId)
				&& resultType == that.resultType;
	}

	@Override
	public int hashCode() {
		return Objects.hash(prizeId, rank, slotNumber, selectionOrder, candidateId, userId, resultType);
	}
}
