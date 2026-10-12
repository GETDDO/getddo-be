package com.getddo.db.drawing.entity;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import com.getddo.core.drawing.domain.DrawPublication;

/** 실제 공개 기록. 취소된 결과도 이 기록에서 제거하지 않는다. */
@Getter
@Entity
@Immutable
@Table(name = "draw_publication_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrawPublicationResultEntity {
	@EmbeddedId
	private DrawPublicationResultId id;
	@Column(name = "event_id", nullable = false, updatable = false, length = 16)
	private UUID eventId;
	@Column(name = "draw_run_id", nullable = false, updatable = false, length = 16)
	private UUID runId;
	@Column(name = "confirmation_id", updatable = false, length = 16)
	private UUID confirmationId;

	public DrawPublicationResultEntity(UUID publicationId, UUID eventId, DrawPublication.Entry entry) {
		id = new DrawPublicationResultId(publicationId, entry.resultId());
		this.eventId = eventId;
		runId = entry.runId();
		confirmationId = entry.confirmationId();
	}

	public DrawPublication.Entry toDomain() {
		return new DrawPublication.Entry(id.getResultId(), runId, confirmationId);
	}
}
