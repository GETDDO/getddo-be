package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.EqualsAndHashCode;

/** 실행 결과 취소와 이 실행을 발생시킨 취소를 함께 조회하는 관리자용 값. */
@Getter
@EqualsAndHashCode
public final class DrawCancellationView {
	private final UUID id;
	private final UUID resultId;
	private final UUID replacementRunId;
	private final UUID canceledBy;
	private final String reason;
	private final Instant canceledAt;

	/** 결과의 취소와 후속 재추첨의 연결을 원본 조회값대로 보존한다. */
	public DrawCancellationView(UUID id, UUID resultId, UUID replacementRunId, UUID canceledBy, String reason, Instant canceledAt) {
		this.id = id;
		this.resultId = resultId;
		this.replacementRunId = replacementRunId;
		this.canceledBy = canceledBy;
		this.reason = reason;
		this.canceledAt = canceledAt;
	}
}
