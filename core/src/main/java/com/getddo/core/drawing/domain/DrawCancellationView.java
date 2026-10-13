package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 실행 결과 취소와 이 실행을 발생시킨 취소를 함께 조회하는 관리자용 값. */
@Getter
@EqualsAndHashCode
@Accessors(fluent = true)
public final class DrawCancellationView {
	private final UUID id;
	private final UUID resultId;
	private final UUID replacementRunId;
	private final UUID canceledBy;
	private final String reason;
	private final Instant canceledAt;

	/** 저장된 조회 필드를 보존하고 컬렉션은 외부 변경으로부터 보호한다. */
	public DrawCancellationView(UUID id, UUID resultId, UUID replacementRunId, UUID canceledBy, String reason, Instant canceledAt) {
		this.id = id;
		this.resultId = resultId;
		this.replacementRunId = replacementRunId;
		this.canceledBy = canceledBy;
		this.reason = reason;
		this.canceledAt = canceledAt;
	}
}
