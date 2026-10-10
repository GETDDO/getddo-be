package com.getddo.db.drawing.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.getddo.db.common.entity.BaseEntity;

/** 최초 후보 상세 정보. 참가자 참조는 UUID이며 확정 후 갱신하지 않는다. */
@Getter
@Entity
@Immutable
@Table(name = "draw_candidates", uniqueConstraints =
		@UniqueConstraint(name = "uq_draw_candidates_1", columnNames = {"draw_run_id", "participant_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrawCandidateEntity extends BaseEntity {
	@Column(name = "participant_id", nullable = false, updatable = false, length = 16)
	private UUID participantId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "draw_run_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_draw_candidates_1"))
	private DrawRunEntity drawRun;

	@Column(name = "ticket_count", nullable = false, updatable = false)
	private long ticketCount;

	@Column(name = "weight", nullable = false, updatable = false)
	private long weight;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "entry_snapshot", nullable = false, updatable = false, columnDefinition = "json")
	private String entrySnapshot;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "eligibility_snapshot", nullable = false, updatable = false, columnDefinition = "json")
	private String eligibilitySnapshot;

	public DrawCandidateEntity(DrawRunEntity drawRun, UUID participantId, long ticketCount, long weight,
			String entrySnapshot, String eligibilitySnapshot) {
		this.drawRun = drawRun;
		this.participantId = participantId;
		this.ticketCount = ticketCount;
		this.weight = weight;
		this.entrySnapshot = entrySnapshot;
		this.eligibilitySnapshot = eligibilitySnapshot;
	}
}
