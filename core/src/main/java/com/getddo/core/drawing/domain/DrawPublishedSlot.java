package com.getddo.core.drawing.domain;

import java.util.UUID;
import java.util.Objects;

import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 실제 공개 이력에서 복원한 자리와 사용자. 현재 취소 상태로 필터링하지 않는다. */
@Getter
@EqualsAndHashCode
@Accessors(fluent = true)
public final class DrawPublishedSlot {
	private final UUID prizeId;
	private final int slotNumber;
	private final UUID resultId;
	private final UUID userId;

	/** 경품의 양수 자리 번호와 실제 공개된 결과·사용자를 검증해 보존한다. */
	public DrawPublishedSlot(UUID prizeId, int slotNumber, UUID resultId, UUID userId) {
		this.prizeId = Objects.requireNonNull(prizeId, "prizeId");
		if (slotNumber < 1) throw new IllegalArgumentException("slotNumber must be positive");
		this.slotNumber = slotNumber;
		this.resultId = Objects.requireNonNull(resultId, "resultId");
		this.userId = Objects.requireNonNull(userId, "userId");
	}
}
