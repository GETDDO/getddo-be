package com.getddo.core.drawing.domain;

import java.util.UUID;
import java.util.Objects;

import lombok.Getter;

/** 기존 취소 행의 공개용 읽기 모델. 취소 처리·재추첨 연결의 소유권은 GD-138에 있다. */
@Getter
public final class DrawPublicationCancellation {
	private final UUID id;
	private final UUID resultId;
	private final UUID replacementRunId;
	private final String reason;

	/** replacementRunId가 null이면 과거 취소에 재추첨 실행이 아직 연결되지 않은 상태다. */
	public DrawPublicationCancellation(UUID id, UUID resultId, UUID replacementRunId, String reason) {
		this.id = Objects.requireNonNull(id, "id");
		this.resultId = Objects.requireNonNull(resultId, "resultId");
		this.replacementRunId = replacementRunId;
		this.reason = reason;
	}
}
