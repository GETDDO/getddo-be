package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 취소 근거와 이미 등록된 재추첨 실행의 불변 연결. 응모권에는 영향을 주지 않는다. */
public record AwardCancellation(UUID id, UUID drawResultId, UUID replacementRunId,
		UUID canceledBy, String reason, Instant canceledAt, UUID requestKey) {
	/** 기존 이력과 영속성 테스트의 키 없는 취소를 복원한다. 신규 등록은 키를 필수 검증한다. */
	public AwardCancellation(UUID id, UUID drawResultId, UUID replacementRunId,
			UUID canceledBy, String reason, Instant canceledAt) {
		this(id, drawResultId, replacementRunId, canceledBy, reason, canceledAt, null);
	}
	public AwardCancellation {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(drawResultId, "drawResultId");
		Objects.requireNonNull(replacementRunId, "replacementRunId");
		Objects.requireNonNull(canceledBy, "canceledBy");
		Objects.requireNonNull(canceledAt, "canceledAt");
		if (reason != null) reason = reason.strip();
		if (reason == null || reason.isBlank() || reason.length() > 1000) {
			throw new IllegalArgumentException("취소 사유는 1~1000자여야 합니다.");
		}
	}
}
