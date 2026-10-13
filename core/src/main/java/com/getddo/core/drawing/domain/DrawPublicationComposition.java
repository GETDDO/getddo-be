package com.getddo.core.drawing.domain;

import java.util.List;
import java.util.UUID;
import java.util.Objects;

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
	private final List<DrawPublicationExecution> executions;

	/** 전체 준비 여부와 공개 가능 명단을 보존하며 최초 결과의 확인 정보는 null을 허용한다. */
	public DrawPublicationComposition(boolean complete, UUID sourceRunId, String reason,
			DrawResultConfirmation confirmation, List<DrawPublication.Entry> entries) {
		this(complete, sourceRunId, reason, confirmation, entries, List.of());
	}

	/** 전체 명단의 준비 여부와 원본 실행·관리자 확인 근거를 함께 보존한다. 미공개 명단도 구성할 수 있다. */
	public DrawPublicationComposition(boolean complete, UUID sourceRunId, String reason,
			DrawResultConfirmation confirmation, List<DrawPublication.Entry> entries, List<DrawPublicationExecution> executions) {
		this.complete = complete;
		this.sourceRunId = Objects.requireNonNull(sourceRunId, "sourceRunId");
		this.reason = reason;
		this.confirmation = confirmation;
		this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
		this.executions = List.copyOf(Objects.requireNonNull(executions, "executions"));
	}
}
