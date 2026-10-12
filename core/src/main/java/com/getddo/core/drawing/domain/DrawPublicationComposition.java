package com.getddo.core.drawing.domain;

import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.experimental.Accessors;

/** 공개할 전체 명단과 그 계기. 최초 공개 가능 여부와 실행의 내부 확정을 구분한다. */
@Getter
@Accessors(fluent = true)
public final class DrawPublicationComposition {
	private final boolean complete;
	private final UUID sourceRunId;
	private final String reason;
	private final DrawResultConfirmation confirmation;
	private final List<DrawPublication.Entry> entries;

	public DrawPublicationComposition(boolean complete, UUID sourceRunId, String reason,
			DrawResultConfirmation confirmation, List<DrawPublication.Entry> entries) {
		this.complete = complete;
		this.sourceRunId = sourceRunId;
		this.reason = reason;
		this.confirmation = confirmation;
		this.entries = List.copyOf(entries);
	}
}
