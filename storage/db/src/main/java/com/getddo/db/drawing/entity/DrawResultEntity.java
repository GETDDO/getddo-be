package com.getddo.db.drawing.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import com.getddo.core.drawing.domain.DrawSelection;
import com.getddo.core.drawing.domain.DrawSelectionResultType;
import com.getddo.db.common.entity.BaseEntity;

/** 당첨과 미충원 자리의 불변 이력. 후보는 해당 실행의 연결을 복합 FK로 참조한다. */
@Getter
@Entity
@Immutable
@Table(name = "draw_results", uniqueConstraints = {
		@UniqueConstraint(name = "uq_draw_results_run", columnNames = {"id", "draw_run_id"}),
		@UniqueConstraint(name = "uq_draw_results_1", columnNames = {"draw_run_id", "event_prize_id", "slot_number"}),
		@UniqueConstraint(name = "uq_draw_results_2", columnNames = {"draw_run_id", "candidate_id"}),
		@UniqueConstraint(name = "uq_draw_results_3", columnNames = {"draw_run_id", "selection_order"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrawResultEntity extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "draw_run_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_draw_results_1"))
	private DrawRunEntity drawRun;

	@Column(name = "event_prize_id", nullable = false, updatable = false, length = 16)
	private UUID prizeId;

	@Column(name = "slot_number", nullable = false, updatable = false)
	private int slotNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "result_type", nullable = false, updatable = false)
	private DrawSelectionResultType resultType;

	@Column(name = "candidate_id", updatable = false, length = 16)
	private UUID candidateId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumns(value = {
			@JoinColumn(name = "draw_run_id", referencedColumnName = "draw_run_id", insertable = false, updatable = false),
			@JoinColumn(name = "candidate_id", referencedColumnName = "candidate_id", insertable = false, updatable = false)
	}, foreignKey = @ForeignKey(name = "fk_draw_results_3"))
	private DrawRunCandidateEntity runCandidate;

	@Column(name = "selection_order", updatable = false)
	private Integer selectionOrder;

	public DrawResultEntity(DrawRunEntity run, DrawSelection selection) {
		drawRun = run;
		prizeId = selection.getPrizeId();
		slotNumber = selection.getSlotNumber();
		resultType = selection.getResultType();
		candidateId = selection.getCandidateId();
		selectionOrder = selection.getSelectionOrder();
	}
}
