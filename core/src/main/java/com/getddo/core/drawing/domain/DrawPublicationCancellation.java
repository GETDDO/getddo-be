package com.getddo.core.drawing.domain;

import java.util.UUID;

import lombok.Getter;
import lombok.experimental.Accessors;

/** 기존 취소 행의 공개용 읽기 모델. 취소 처리·재추첨 연결의 소유권은 GD-138에 있다. */
@Getter
@Accessors(fluent = true)
public final class DrawPublicationCancellation {
	private final UUID id;
	private final UUID resultId;
	private final UUID replacementRunId;
	private final String reason;

	public DrawPublicationCancellation(UUID id, UUID resultId, UUID replacementRunId, String reason) {
		this.id = id;
		this.resultId = resultId;
		this.replacementRunId = replacementRunId;
		this.reason = reason;
	}
}
