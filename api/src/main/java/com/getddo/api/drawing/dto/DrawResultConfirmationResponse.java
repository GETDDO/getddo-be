package com.getddo.api.drawing.dto;

import java.time.Instant;
import java.util.UUID;

import com.getddo.core.drawing.domain.DrawPublication;
import com.getddo.core.drawing.domain.DrawPublicationConfirmation;
import com.getddo.core.drawing.domain.DrawResultConfirmation;

/** 확인 저장과 실제 공개 여부를 구분한다. 미공개이면 공개 식별자·버전·시각은 null이다. */
public record DrawResultConfirmationResponse(UUID eventId, UUID confirmationId, UUID runId,
		UUID confirmedBy, Instant confirmedAt, boolean isPublished, UUID publicationId, Integer revision,
		Instant publishedAt) {
	public static DrawResultConfirmationResponse from(DrawPublicationConfirmation result) {
		DrawResultConfirmation confirmation = result.confirmation();
		DrawPublication publication = result.publication();
		return new DrawResultConfirmationResponse(result.eventId(), confirmation.id(), confirmation.runId(),
				confirmation.confirmedBy(), confirmation.confirmedAt(), publication != null,
				publication == null ? null : publication.id(), publication == null ? null : publication.revision(),
				publication == null ? null : publication.publishedAt());
	}
}
