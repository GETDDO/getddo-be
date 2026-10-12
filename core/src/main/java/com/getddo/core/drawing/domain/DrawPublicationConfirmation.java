package com.getddo.core.drawing.domain;

import java.util.UUID;
import java.util.Objects;

import lombok.Getter;
import lombok.experimental.Accessors;

/** 같은 트랜잭션에서 확인한 관리자 확인 기록과 실제 최신 공개 버전이다. 공개 전에는 publication이 null이다. */
@Getter
@Accessors(fluent = true)
public final class DrawPublicationConfirmation {
	private final UUID eventId;
	private final DrawResultConfirmation confirmation;
	private final DrawPublication publication;

	public DrawPublicationConfirmation(UUID eventId, DrawResultConfirmation confirmation, DrawPublication publication) {
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.confirmation = Objects.requireNonNull(confirmation, "confirmation");
		if (publication != null && !eventId.equals(publication.eventId()))
			throw new IllegalArgumentException("publication belongs to another event");
		this.publication = publication;
	}
}
