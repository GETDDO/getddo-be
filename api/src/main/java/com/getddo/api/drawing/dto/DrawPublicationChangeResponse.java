package com.getddo.api.drawing.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.getddo.core.drawing.domain.DrawPublication;
import com.getddo.core.drawing.domain.DrawPublicationChange;
import com.getddo.core.drawing.domain.DrawPublicationExecution;
import com.getddo.core.drawing.domain.DrawPublicationCancellation;

/** 실제 공개 버전 간 변경만 제공한다. 취소 여부나 미확인 결과 조회를 대신하지 않는다. */
public record DrawPublicationChangeResponse(UUID id, UUID publicationId, int revision, UUID drawRunId,
		String reason, UUID confirmedBy, Instant confirmedAt, Instant updatedAt,
		List<SlotChange> changes, List<Execution> executions) {
	/** 실제 공개 버전과 실행별 근거를 기존 변경 이력 필드에 대응시킨다. */
	public static DrawPublicationChangeResponse from(DrawPublicationChange change) {
		DrawPublication publication = change.publication();
		return new DrawPublicationChangeResponse(publication.id(), publication.id(), publication.revision(),
				publication.sourceDrawId(), publication.reason(), publication.confirmedBy(),
				publication.confirmedAt(), publication.publishedAt(),
				change.changes().stream().map(SlotChange::from).toList(),
				change.executions().stream().map(Execution::from).toList());
	}

	public record Execution(UUID runId, UUID confirmationId, UUID confirmedBy, Instant confirmedAt, List<Cancellation> cancellations) {
		/** 한 실행의 확인 근거와 연결 취소를 함께 변환한다. */
		static Execution from(DrawPublicationExecution execution) {
			return new Execution(execution.runId(), execution.confirmationId(), execution.confirmedBy(), execution.confirmedAt(),
					execution.cancellations().stream().map(Cancellation::from).toList());
		}
	}
	public record Cancellation(UUID id, UUID resultId, String reason) {
		/** 공개 근거에 필요한 취소 식별자와 사유만 반환한다. */
		static Cancellation from(DrawPublicationCancellation cancellation) {
			return new Cancellation(cancellation.id(), cancellation.resultId(), cancellation.reason());
		}
	}

	public record SlotChange(UUID prizeId, int slotNumber, UUID beforeUserId, UUID afterUserId) {
		/** 경품 자리의 공개 전후 사용자만 응답 값으로 변환한다. */
		static SlotChange from(DrawPublicationChange.SlotChange change) {
			return new SlotChange(change.prizeId(), change.slotNumber(), change.beforeUserId(), change.afterUserId());
		}
	}
}
