package com.getddo.db.drawing.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** 검증 결과는 원본 추첨 자료와 분리된 불변 이력으로 저장한다. */
@Getter
@Entity
@Immutable
@Table(name = "draw_checks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrawCheckEntity {
	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false, length = 16)
	private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "draw_run_id", nullable = false, updatable = false)
	private DrawRunEntity drawRun;
	@Column(name = "checked_by", nullable = false, updatable = false, length = 16)
	private UUID checkedBy;
	@Column(nullable = false, updatable = false)
	private boolean passed;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "checks_snapshot", nullable = false, updatable = false, columnDefinition = "json")
	private String checksSnapshot;
	@Column(name = "checked_at", nullable = false, updatable = false)
	private Instant checkedAt;

	public DrawCheckEntity(DrawRunEntity drawRun, UUID checkedBy, boolean passed,
			String checksSnapshot, Instant checkedAt) {
		this.drawRun = Objects.requireNonNull(drawRun, "drawRun");
		this.checkedBy = Objects.requireNonNull(checkedBy, "checkedBy");
		this.passed = passed;
		this.checksSnapshot = Objects.requireNonNull(checksSnapshot, "checksSnapshot");
		this.checkedAt = Objects.requireNonNull(checkedAt, "checkedAt");
	}
}
