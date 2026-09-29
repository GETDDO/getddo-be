package com.getddo.core.drawing.domain;

import java.util.Objects;
import java.util.UUID;

/** 추첨할 경품의 등수와 설정 당첨 인원이다. */
public record DrawPrize(UUID prizeId, int rank, int winnerCount) {

	public DrawPrize {
		Objects.requireNonNull(prizeId, "prizeId");
		if (rank <= 0) {
			throw new IllegalArgumentException("rank must be positive");
		}
		if (winnerCount <= 0) {
			throw new IllegalArgumentException("winnerCount must be positive");
		}
	}
}
