package com.getddo.api.drawing.dto.response;
import java.time.Instant;
import java.util.UUID;
import com.getddo.core.drawing.domain.DrawFailure;
import com.getddo.core.drawing.domain.DrawFailureCode;

/** 저장된 분류 코드와 안전한 안내 문구만 제공한다. 원문 예외를 응답하지 않는다. */
public record DrawFailureResponse(UUID id, UUID runId, String failureCode, String message, Instant failedAt, String traceId) {
	/** 실패 기록을 원문 예외 없이 관리자용 응답으로 변환한다. */
	public static DrawFailureResponse from(DrawFailure failure) {
		return new DrawFailureResponse(failure.getId(), failure.getRunId(), failure.getFailureCode(), message(failure.getFailureCode()),
				failure.getFailedAt(), failure.getTraceId());
	}
	/** 과거 미분류 코드도 안전한 기본 문구로 처리한다. */
	private static String message(String code) {
		try {
			return switch (DrawFailureCode.valueOf(code)) {
				case TRANSIENT_STORAGE -> "일시적인 저장 오류로 추첨에 실패했습니다.";
				case INTERRUPTED -> "추첨 처리 시도가 중단되었습니다.";
				case INVALID_EVIDENCE -> "추첨 입력 또는 결과의 정합성을 확인해야 합니다.";
				case INVALID_RUN -> "추첨 실행 상태를 확인해야 합니다.";
				case NOT_EXECUTABLE -> "현재 이벤트 상태에서는 추첨할 수 없습니다.";
				case INTERNAL_ERROR -> "추첨 처리 중 오류가 발생했습니다. 운영자 확인이 필요합니다.";
			};
		} catch (IllegalArgumentException unknown) { return "추첨 처리 실패 이력을 확인해야 합니다."; }
	}
}
