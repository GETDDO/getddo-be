package com.getddo.db.drawing.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import com.getddo.core.drawing.domain.AwardCancellation;

/** 기존 award_cancellations 스키마를 매핑한다. created_at 컬럼이 없어 BaseEntity를 상속하지 않는다. */
@Getter
@Entity
@Immutable
@Table(name = "award_cancellations", uniqueConstraints =
		{
			@UniqueConstraint(name = "uq_award_cancellations_1", columnNames = "draw_result_id"),
			@UniqueConstraint(name = "uq_award_cancellations_request", columnNames = "request_key")
		})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AwardCancellationEntity {
	@Id
	@Column(nullable = false, updatable = false, length = 16)
	private UUID id;
	@Column(name = "draw_result_id", nullable = false, updatable = false, length = 16)
	private UUID drawResultId;
	@Column(name = "draw_run_id", updatable = false, length = 16)
	private UUID replacementRunId;
	@Column(name = "canceled_by", nullable = false, updatable = false, length = 16)
	private UUID canceledBy;
	@Column(nullable = false, updatable = false, columnDefinition = "text")
	private String reason;
	@Column(name = "canceled_at", nullable = false, updatable = false)
	private Instant canceledAt;
	@Column(name = "request_key", updatable = false, length = 16)
	private UUID requestKey;

	public AwardCancellationEntity(AwardCancellation cancellation) {
		id = cancellation.id();
		drawResultId = cancellation.drawResultId();
		replacementRunId = cancellation.replacementRunId();
		canceledBy = cancellation.canceledBy();
		reason = cancellation.reason();
		canceledAt = cancellation.canceledAt();
		requestKey = cancellation.requestKey();
	}
}
