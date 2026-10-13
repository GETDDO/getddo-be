package com.getddo.api.drawing.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.getddo.core.drawing.domain.DrawPublication;
import com.getddo.core.drawing.domain.DrawPublicationChange;

/** 실제 공개 버전 간 변경만 제공한다. 취소 여부나 미확인 결과 조회를 대신하지 않는다. */
public record DrawPublicationChangeResponse(UUID id, UUID publicationId, int revision, UUID drawRunId,
		String reason, UUID confirmedBy, Instant confirmedAt, Instant updatedAt,
		List<SlotChange> changes) {
	/** 실제 공개 버전의 메타데이터와 자리별 사용자 변경을 응답 값으로 변환한다. */
	public static DrawPublicationChangeResponse from(DrawPublicationChange change) {
		DrawPublication publication = change.publication();
		return new DrawPublicationChangeResponse(publication.id(), publication.id(), publication.revision(),
				publication.sourceDrawId(), publication.reason(), publication.confirmedBy(),
				publication.confirmedAt(), publication.publishedAt(), change.changes().stream().map(SlotChange::from).toList());
	}

	public record SlotChange(UUID prizeId, int slotNumber, UUID beforeUserId, UUID afterUserId) {
		/** 경품 자리의 공개 전후 사용자만 응답 값으로 변환한다. */
		static SlotChange from(DrawPublicationChange.SlotChange change) {
			return new SlotChange(change.prizeId(), change.slotNumber(), change.beforeUserId(), change.afterUserId());
		}
	}
}
