package com.getddo.db.drawing.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.getddo.core.drawing.domain.DrawFailure;

/** 실제 실패 스키마에는 created_at이 없어 BaseEntity를 상속하지 않는다. */
@Getter
@Entity
@Table(name = "draw_failures")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrawFailureEntity {
	@Id @Column(nullable = false, updatable = false, length = 16)
	private UUID id;
	@Column(name = "draw_run_id", nullable = false, updatable = false, length = 16)
	private UUID runId;
	@Column(name = "failure_code", nullable = false, updatable = false, length = 50)
	private String failureCode;
	@Column(name = "failed_at", nullable = false, updatable = false)
	private Instant failedAt;
	@Column(name = "trace_id", updatable = false, length = 100)
	private String traceId;
	/** 실패 ID·실행 참조·코드·시각·추적 ID를 그대로 보존해 동일 실패 저장의 멱등성을 유지한다. */
	public DrawFailureEntity(DrawFailure failure) {
		id = failure.getId();
		runId = failure.getRunId();
		failureCode = failure.getFailureCode();
		failedAt = failure.getFailedAt();
		traceId = failure.getTraceId();
	}
}
