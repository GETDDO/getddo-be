package com.getddo.core.drawing.domain;

import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/** 공통 실행의 내부 응답. 미확정 상태에는 결과를 포함하지 않는다. */
@Getter
public final class DrawExecutionResult {
	private final UUID runId;
	private final DrawRunStatus status;
	private final DrawConfirmation confirmation;

	/** 실행 상태와 확정 결과를 묶는다. CONFIRMED에만 같은 실행의 저장 결과가 존재하도록 생성 시 검증한다. */
	public DrawExecutionResult(UUID runId, DrawRunStatus status, DrawConfirmation confirmation) {
		this.runId = Objects.requireNonNull(runId);
		this.status = Objects.requireNonNull(status);
		if ((status == DrawRunStatus.CONFIRMED) != (confirmation != null)
				|| (confirmation != null && !runId.equals(confirmation.getRunId()))) {
			throw new IllegalArgumentException("확정 상태와 저장 결과가 일치해야 합니다.");
		}
		this.confirmation = confirmation;
	}
}
