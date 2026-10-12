package com.getddo.api.drawing.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.getddo.core.drawing.domain.DrawPublication;
import com.getddo.core.drawing.domain.DrawPublicationChange;

/** 실제 공개 버전 간 변경만 제공한다. 취소 여부나 미확인 결과 조회를 대신하지 않는다. */
public record DrawPublicationChangeResponse(UUID id, UUID publicationId, int revision, UUID drawRunId,
		String reason, UUID confirmedBy, Instant confirmedAt, Instant updatedAt,
		List<SlotChange> changes, List<Execution> executions) {
	public static DrawPublicationChangeResponse from(DrawPublicationChange change) {
		DrawPublication publication = change.publication();
		return new DrawPublicationChangeResponse(publication.id(), publication.id(), publication.revision(),
				publication.sourceDrawId(), publication.reason(), publication.confirmedBy(),
				publication.confirmedAt(), publication.publishedAt(), change.changes().stream().map(SlotChange::from).toList(), change.executions().stream().map(Execution::from).toList());
	}

	public record Execution(UUID runId, UUID confirmationId, UUID confirmedBy, Instant confirmedAt, List<Cancellation> cancellations) {
		static Execution from(com.getddo.core.drawing.domain.DrawPublicationExecution execution) {
			return new Execution(execution.runId(), execution.confirmationId(), execution.confirmedBy(), execution.confirmedAt(),
					execution.cancellations().stream().map(value -> new Cancellation(value.id(), value.resultId(), value.reason())).toList());
		}
	}
	public record Cancellation(UUID id, UUID resultId, String reason) { }

	public record SlotChange(UUID prizeId, int slotNumber, UUID beforeUserId, UUID afterUserId) {
		static SlotChange from(DrawPublicationChange.SlotChange change) {
			return new SlotChange(change.prizeId(), change.slotNumber(), change.beforeUserId(), change.afterUserId());
		}
	}
}
