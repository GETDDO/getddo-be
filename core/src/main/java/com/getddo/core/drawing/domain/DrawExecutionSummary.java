package com.getddo.core.drawing.domain;
import java.time.Instant;
import java.util.List;
/** 실행 상세의 실패·예약·운영자 대기·취소 요약. 전체 이력은 별도로 페이징한다. */
public record DrawExecutionSummary(long failureCount, DrawFailure latestFailure, Instant retryAt,
		boolean waitingForOperator, List<DrawCancellationView> cancellations) {
	public DrawExecutionSummary { cancellations = List.copyOf(cancellations); }
}
