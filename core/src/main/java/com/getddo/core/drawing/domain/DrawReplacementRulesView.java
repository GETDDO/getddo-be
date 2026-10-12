package com.getddo.core.drawing.domain;

import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** GD-87 버전 2 JSON 계약의 읽기 전용 투영. 실행·입력 준비 인터페이스를 대신하지 않는다. */
@Getter
public final class DrawReplacementRulesView {
	private final UUID originalRunId;
	private final List<Slot> targetSlots;
	private final List<RetainedAward> retainedAwards;

	public DrawReplacementRulesView(UUID originalRunId, List<Slot> targetSlots, List<RetainedAward> retainedAwards) {
		this.originalRunId = originalRunId;
		this.targetSlots = targetSlots == null ? List.of() : List.copyOf(targetSlots);
		this.retainedAwards = retainedAwards == null ? List.of() : List.copyOf(retainedAwards);
	}

	@Getter
	@RequiredArgsConstructor
	public static final class Slot {
		private final UUID prizeId;
		private final int rank;
		private final int slotNumber;
	}

	@Getter
	@RequiredArgsConstructor
	public static final class RetainedAward {
		private final UUID resultId;
		private final UUID userId;
		private final UUID prizeId;
		private final int slotNumber;
	}
}
