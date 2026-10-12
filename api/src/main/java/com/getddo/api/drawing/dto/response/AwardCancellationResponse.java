package com.getddo.api.drawing.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.getddo.core.drawing.domain.AwardCancellation;
import com.getddo.core.drawing.domain.DrawRunStatus;

/** AD05 응답. 상태는 실제 연결 실행에서 조회한 값이며 DTO에서 실행 성공을 추정하지 않는다. */
public record AwardCancellationResponse(UUID cancellationId, UUID canceledDrawResultId,
		UUID replacementDrawRunId, DrawRunStatus status, Instant canceledAt) {
	public static AwardCancellationResponse from(AwardCancellation cancellation, DrawRunStatus status) {
		return new AwardCancellationResponse(cancellation.id(), cancellation.drawResultId(),
				cancellation.replacementRunId(), status, cancellation.canceledAt());
	}
}
