package com.getddo.core.drawing.domain;

import java.util.UUID;

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

	public DrawPublishedSlot(UUID prizeId, int slotNumber, UUID resultId, UUID userId) {
		this.prizeId = prizeId;
		this.slotNumber = slotNumber;
		this.resultId = resultId;
		this.userId = userId;
	}
}
