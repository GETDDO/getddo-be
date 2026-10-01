package com.getddo.core.drawing.domain;

import java.util.Objects;
import java.util.UUID;

/** 추첨할 경품의 등수와 설정 당첨 인원이다. */
public final class DrawPrize {

	private final UUID prizeId;
	private final int rank;
	private final int winnerCount;

	public DrawPrize(UUID prizeId, int rank, int winnerCount) {
		this.prizeId = Objects.requireNonNull(prizeId, "prizeId");
		if (rank <= 0) {
			throw new IllegalArgumentException("rank must be positive");
		}
		if (winnerCount <= 0) {
			throw new IllegalArgumentException("winnerCount must be positive");
		}
		this.rank = rank;
		this.winnerCount = winnerCount;
	}

	public UUID getPrizeId() {
		return prizeId;
	}

	public int getRank() {
		return rank;
	}

	public int getWinnerCount() {
		return winnerCount;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof DrawPrize that)) {
			return false;
		}
		return rank == that.rank
				&& winnerCount == that.winnerCount
				&& prizeId.equals(that.prizeId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(prizeId, rank, winnerCount);
	}

	@Override
	public String toString() {
		return "DrawPrize[prizeId=" + prizeId + ", rank=" + rank + ", winnerCount=" + winnerCount + "]";
	}
}
