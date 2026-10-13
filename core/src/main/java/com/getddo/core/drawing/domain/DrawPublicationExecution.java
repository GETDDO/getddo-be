package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 공개 버전에 실제 반영한 실행별 확인 근거. 미충원 실행도 포함한다. */
@Getter
@EqualsAndHashCode
@Accessors(fluent = true)
public final class DrawPublicationExecution {
	private final UUID runId;
	private final UUID confirmationId;
	private final UUID confirmedBy;
	private final Instant confirmedAt;
	private final List<DrawPublicationCancellation> cancellations;

	/** 저장된 조회 필드를 보존하고 컬렉션은 외부 변경으로부터 보호한다. */
	public DrawPublicationExecution(UUID runId, UUID confirmationId, UUID confirmedBy, Instant confirmedAt, List<DrawPublicationCancellation> cancellations) {
		this.runId = Objects.requireNonNull(runId, "runId");
		this.confirmationId = Objects.requireNonNull(confirmationId, "confirmationId");
		this.confirmedBy = Objects.requireNonNull(confirmedBy, "confirmedBy");
		this.confirmedAt = Objects.requireNonNull(confirmedAt, "confirmedAt");
		this.cancellations = List.copyOf(Objects.requireNonNull(cancellations, "cancellations"));
	}
}
