package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.EqualsAndHashCode;

/** 관리자 상세에 사용하는 최근 실패·재시도·취소 요약. 전체 이력은 별도 조회한다. */
@Getter
@EqualsAndHashCode
public final class DrawExecutionSummary {
	private final long failureCount;
	private final DrawFailure latestFailure;
	private final Instant retryAt;
	private final boolean waitingForOperator;
	private final List<DrawCancellationView> cancellations;

	/** 저장된 조회 필드를 보존하고 컬렉션은 외부 변경으로부터 보호한다. */
	public DrawExecutionSummary(long failureCount, DrawFailure latestFailure, Instant retryAt, boolean waitingForOperator, List<DrawCancellationView> cancellations) {
		this.failureCount = failureCount;
		this.latestFailure = latestFailure;
		this.retryAt = retryAt;
		this.waitingForOperator = waitingForOperator;
		this.cancellations = List.copyOf(Objects.requireNonNull(cancellations, "cancellations"));
	}
}
