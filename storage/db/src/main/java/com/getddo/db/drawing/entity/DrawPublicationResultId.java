package com.getddo.db.drawing.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 한 버전에 실제 포함된 결과의 복합 식별자. */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrawPublicationResultId implements Serializable {
	@Column(name = "publication_id", nullable = false, updatable = false, length = 16)
	private UUID publicationId;
	@Column(name = "draw_result_id", nullable = false, updatable = false, length = 16)
	private UUID resultId;

	/** 공개 버전과 결과 식별자를 복합키로 묶는다. */
	public DrawPublicationResultId(UUID publicationId, UUID resultId) {
		this.publicationId = publicationId;
		this.resultId = resultId;
	}
}
