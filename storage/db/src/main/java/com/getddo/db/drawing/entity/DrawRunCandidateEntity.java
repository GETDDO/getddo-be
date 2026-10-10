package com.getddo.db.drawing.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

/** 실행별 후보 명단. 복합 PK와 두 FK로 최초 후보를 재사용한다. */
@Getter
@Entity
@Immutable
@Table(name = "draw_run_candidates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrawRunCandidateEntity implements Persistable<DrawRunCandidateId> {
	@EmbeddedId
	private DrawRunCandidateId id;

	@MapsId("drawRunId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "draw_run_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_draw_run_candidates_1"))
	private DrawRunEntity drawRun;

	@MapsId("candidateId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "candidate_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_draw_run_candidates_2"))
	private DrawCandidateEntity candidate;

	@Transient
	private boolean newEntity = true;

	public DrawRunCandidateEntity(DrawRunEntity drawRun, DrawCandidateEntity candidate) {
		this.drawRun = drawRun;
		this.candidate = candidate;
		id = new DrawRunCandidateId(drawRun.getId(), candidate.getId());
	}

	@Override
	public boolean isNew() { return newEntity; }

	@PostLoad
	@PostPersist
	private void markPersisted() { newEntity = false; }
}
