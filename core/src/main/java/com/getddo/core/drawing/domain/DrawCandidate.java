package com.getddo.core.drawing.domain;

import java.util.Objects;
import java.util.UUID;

/** 추첨 실행에 전달된 후보와 확정 가중치다. 응모권 수를 가중치로 바꾸는 작업은 호출자가 수행한다. */
public record DrawCandidate(UUID candidateId, UUID userId, long weight) {

	public DrawCandidate {
		Objects.requireNonNull(candidateId, "candidateId");
		Objects.requireNonNull(userId, "userId");
		if (weight <= 0) {
			throw new IllegalArgumentException("weight must be positive");
		}
	}
}
