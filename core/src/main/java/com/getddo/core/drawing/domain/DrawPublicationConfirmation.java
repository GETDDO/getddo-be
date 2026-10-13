package com.getddo.core.drawing.domain;

import java.util.UUID;
import java.util.Objects;

import lombok.Getter;

/** 같은 트랜잭션에서 확인한 관리자 확인 기록과 실제 최신 공개 버전이다. 공개 전에는 publication이 null이다. */
@Getter
public final class DrawPublicationConfirmation {
	private final UUID eventId;
	private final DrawResultConfirmation confirmation;
	private final DrawPublication publication;

	/** 필수 확인 기록과 실제 공개 버전을 구분하며 아직 미공개이면 공개 버전은 null이다. */
	public DrawPublicationConfirmation(UUID eventId, DrawResultConfirmation confirmation, DrawPublication publication) {
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.confirmation = Objects.requireNonNull(confirmation, "confirmation");
		if (publication != null && !eventId.equals(publication.getEventId()))
			throw new IllegalArgumentException("publication belongs to another event");
		this.publication = publication;
	}
}
