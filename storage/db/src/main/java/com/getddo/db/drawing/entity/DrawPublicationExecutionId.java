package com.getddo.db.drawing.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 한 버전에 반영한 실행의 복합 식별자. */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrawPublicationExecutionId implements Serializable {
	@Column(name = "publication_id", nullable = false, updatable = false, length = 16)
	private UUID publicationId;
	@Column(name = "draw_run_id", nullable = false, updatable = false, length = 16)
	private UUID runId;

	public DrawPublicationExecutionId(UUID publicationId, UUID runId) {
		this.publicationId = publicationId;
		this.runId = runId;
	}
}
