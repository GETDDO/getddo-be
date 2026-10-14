package com.getddo.core.drawing.domain;

import java.util.Objects;
import java.util.UUID;

/** 추첨 실행에 전달된 후보와 확정 가중치다. 응모권 수를 가중치로 바꾸는 작업은 호출자가 수행한다. */
public final class DrawCandidate {

	private final UUID candidateId;
	private final UUID userId;
	private final long weight;

	public DrawCandidate(UUID candidateId, UUID userId, long weight) {
		this.candidateId = Objects.requireNonNull(candidateId, "candidateId");
		this.userId = Objects.requireNonNull(userId, "userId");
		if (weight <= 0) {
			throw new IllegalArgumentException("weight must be positive");
		}
		this.weight = weight;
	}

	public UUID getCandidateId() {
		return candidateId;
	}

	public UUID getUserId() {
		return userId;
	}

	public long getWeight() {
		return weight;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof DrawCandidate that)) {
			return false;
		}
		return weight == that.weight
				&& candidateId.equals(that.candidateId)
				&& userId.equals(that.userId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(candidateId, userId, weight);
	}
}
