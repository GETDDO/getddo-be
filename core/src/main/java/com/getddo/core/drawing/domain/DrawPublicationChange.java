package com.getddo.core.drawing.domain;

import java.util.List;
import java.util.UUID;
import java.util.Objects;

import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 직전 실제 공개 버전과 비교한 변경 이력. 확인자·사유·실제 공개 시각은 버전에 남는다. */
@Getter
@EqualsAndHashCode
@Accessors(fluent = true)
public final class DrawPublicationChange {
	private final DrawPublication publication;
	private final List<SlotChange> changes;
	private final List<DrawPublicationExecution> executions;

	public DrawPublicationChange(DrawPublication publication, List<SlotChange> changes) {
		this(publication, changes, List.of());
	}

	public DrawPublicationChange(DrawPublication publication, List<SlotChange> changes, List<DrawPublicationExecution> executions) {
		this.publication = Objects.requireNonNull(publication, "publication");
		this.changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
		this.executions = List.copyOf(Objects.requireNonNull(executions, "executions"));
	}

	@Getter
	@EqualsAndHashCode
	@Accessors(fluent = true)
	public static final class SlotChange {
		private final UUID prizeId;
		private final int slotNumber;
		private final UUID beforeResultId;
		private final UUID beforeUserId;
		private final UUID afterResultId;
		private final UUID afterUserId;

		public SlotChange(UUID prizeId, int slotNumber, UUID beforeResultId, UUID beforeUserId,
			UUID afterResultId, UUID afterUserId) {
			this.prizeId = Objects.requireNonNull(prizeId, "prizeId");
			if (slotNumber < 1) throw new IllegalArgumentException("slotNumber must be positive");
			this.slotNumber = slotNumber;
			this.beforeResultId = beforeResultId;
			this.beforeUserId = beforeUserId;
			this.afterResultId = afterResultId;
			this.afterUserId = afterUserId;
		}
	}
}
