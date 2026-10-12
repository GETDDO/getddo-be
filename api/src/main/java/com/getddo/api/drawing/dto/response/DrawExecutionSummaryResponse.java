package com.getddo.api.drawing.dto.response;
import java.time.Instant;
import java.util.List;
import com.getddo.core.drawing.domain.DrawCancellationView;
import com.getddo.core.drawing.domain.DrawExecutionSummary;
/** 관리자 실행 상세의 요약. 전체 실패와 감사 이력은 페이징한다. */
public record DrawExecutionSummaryResponse(long failureCount, DrawFailureResponse latestFailure, Instant retryAt,
		boolean waitingForOperator, List<DrawCancellationView> cancellations) {
	public static DrawExecutionSummaryResponse from(DrawExecutionSummary summary) {
		return summary == null ? null : new DrawExecutionSummaryResponse(summary.failureCount(),
				summary.latestFailure() == null ? null : DrawFailureResponse.from(summary.latestFailure()),
				summary.retryAt(), summary.waitingForOperator(), summary.cancellations());
	}
}
