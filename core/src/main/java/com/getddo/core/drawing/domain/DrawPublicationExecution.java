package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 한 공개 버전에 반영한 실행별 불변 확인 근거. 미충원 실행도 포함한다. */
public record DrawPublicationExecution(UUID runId, UUID confirmationId, UUID confirmedBy,
		Instant confirmedAt, List<DrawPublicationCancellation> cancellations) {
	public DrawPublicationExecution {
		java.util.Objects.requireNonNull(runId);
		java.util.Objects.requireNonNull(confirmationId);
		java.util.Objects.requireNonNull(confirmedBy);
		java.util.Objects.requireNonNull(confirmedAt);
		cancellations = List.copyOf(cancellations);
	}
}
