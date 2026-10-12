package com.getddo.api.drawing.dto;

import java.time.Instant;
import java.util.UUID;

import com.getddo.core.drawing.domain.DrawPublication;

/** 실제 저장된 최신 공개 버전이다. drawRunId는 이 버전의 원본이며 요청 실행과 항상 같지는 않다. */
public record DrawPublicationResponse(UUID eventId, UUID publicationId, UUID drawRunId, int revision,
		Instant publishedAt, Instant updatedAt, UUID updatedBy) {
	public static DrawPublicationResponse from(DrawPublication publication) {
		return new DrawPublicationResponse(publication.eventId(), publication.id(), publication.sourceDrawId(),
				publication.revision(), publication.publishedAt(), publication.publishedAt(), publication.confirmedBy());
	}
}
