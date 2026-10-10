package com.getddo.db.drawing.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrawRunCandidateId implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(name = "draw_run_id", nullable = false, updatable = false, length = 16)
	private UUID drawRunId;

	@Column(name = "candidate_id", nullable = false, updatable = false, length = 16)
	private UUID candidateId;

	public DrawRunCandidateId(UUID drawRunId, UUID candidateId) {
		this.drawRunId = drawRunId;
		this.candidateId = candidateId;
	}
}
