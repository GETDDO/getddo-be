package com.getddo.db.drawing.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.domain.DrawType;
import com.getddo.db.common.entity.BaseEntity;

/** 이벤트 참조는 UUID로 보존하며 후보·조건 확정 후 입력을 변경하지 않는다. */
@Getter
@Entity
@Table(name = "draw_runs", uniqueConstraints = {
			@UniqueConstraint(name = "uq_draw_runs_event", columnNames = {
				"id", "event_id"
			}),
			@UniqueConstraint(name = "uq_draw_runs_1", columnNames = {
				"event_id", "run_number"
			})
		})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrawRunEntity extends BaseEntity {
	@Column(name = "event_id", nullable = false, updatable = false, length = 16)
	private UUID eventId;

	@Column(name = "run_number", nullable = false, updatable = false)
	private int runNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "draw_type", nullable = false, updatable = false)
	private DrawType drawType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private DrawRunStatus status;

	@Column(name = "algorithm_version", length = 100)
	private String algorithmVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "rules_snapshot", columnDefinition = "json")
	private String rulesSnapshot;

	@Column(name = "snapshot_fixed_at")
	private Instant snapshotFixedAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	@Column(name = "attempt_id", length = 16)
	private UUID attemptId;
	@Column(name = "lease_until")
	private Instant leaseUntil;
	@Column(name = "retry_at")
	private Instant retryAt;
	@Column(name = "retry_cycle_started_at")
	private Instant retryCycleStartedAt;

	/** 이벤트의 최초 실행을 PREPARING 상태와 실행 번호 0으로 생성한다. */
	public DrawRunEntity(UUID eventId) {
		this(eventId, 0, DrawType.INITIAL);
	}

	/** 등록과 취소의 원자성은 GD-138 서비스가 관리한다. */
	public DrawRunEntity(UUID eventId, int runNumber, DrawType drawType) {
		if (eventId == null || drawType == null || (drawType == DrawType.INITIAL && runNumber != 0)
				|| (drawType == DrawType.REPLACEMENT && runNumber < 1)) throw new IllegalArgumentException("실행 종류와 차수가 일치해야 합니다.");
		this.eventId = eventId;
		this.runNumber = runNumber;
		this.drawType = drawType;
		status = DrawRunStatus.PREPARING;
	}

	/**
	 * 종류별 후보 준비가 끝난 실행의 입력을 한 번만 확정한다. 새 NO_CANDIDATES는 생성하지 않는다.
	 *
	 * @param status 정상 후보가 있으면 READY, 응모자가 없으면 NO_ENTRIES
	 * @param algorithmVersion 후보 가중치·선정 알고리즘 버전
	 * @param rulesSnapshot 확정된 경품·가중치 조건의 JSON
	 * @param fixedAt 서버 기준 입력 확정 시각
	 * @throws IllegalStateException 이미 입력을 확정했거나 PREPARING 상태가 아닌 경우
	 * @throws IllegalArgumentException 신규 입력 확정 상태가 아니거나 필수 입력이 null인 경우
	 */
	public void fixInput(DrawRunStatus status, String algorithmVersion, String rulesSnapshot, Instant fixedAt) {
		if (this.status != DrawRunStatus.PREPARING || snapshotFixedAt != null) {
			throw new IllegalStateException("추첨 입력은 한 번만 확정할 수 있습니다.");
		}
		if ((status != DrawRunStatus.READY && status != DrawRunStatus.NO_ENTRIES)
				|| (drawType == DrawType.REPLACEMENT && status != DrawRunStatus.READY)) {
			throw new IllegalArgumentException("실행 종류에 맞는 입력 확정 상태가 아닙니다.");
		}
		if (algorithmVersion == null || rulesSnapshot == null || fixedAt == null) {
			throw new IllegalArgumentException("확정 입력은 알고리즘 버전·규칙·확정 시각이 필요합니다.");
		}
		this.status = status;
		this.algorithmVersion = algorithmVersion;
		this.rulesSnapshot = rulesSnapshot;
		snapshotFixedAt = fixedAt;
		if (status == DrawRunStatus.NO_ENTRIES) releaseAttempt();
	}

	/** 결과 저장과 같은 트랜잭션에서만 호출한다. 실패하면 READY와 결과 모두 롤백된다. */
	public void confirm(Instant now) {
		if ((status != DrawRunStatus.READY && status != DrawRunStatus.RUNNING)
				|| now == null || snapshotFixedAt == null || now.isBefore(snapshotFixedAt)) {
			throw new IllegalStateException("저장 입력이 있는 READY/RUNNING 실행만 결과를 확정할 수 있습니다.");
		}
		if (startedAt == null) startedAt = now;
		confirmedAt = now;
		status = DrawRunStatus.CONFIRMED;
		releaseAttempt();
	}

	/** 소유권 확정은 별도 서비스 트랜잭션에서 한다. 입력과 실패 집계는 변경하지 않는다. */
	public void claim(UUID attemptId, Instant now, Instant leaseUntil, Instant cycleStartedAt) {
		if (this.attemptId != null || (status != DrawRunStatus.READY && status != DrawRunStatus.PREPARING
				&& status != DrawRunStatus.FAILED) || attemptId == null || now == null
				|| leaseUntil == null || cycleStartedAt == null || !leaseUntil.isAfter(now)) {
			throw new IllegalStateException("실행 소유권을 얻을 수 없습니다.");
		}
		this.attemptId = attemptId;
		this.leaseUntil = leaseUntil;
		retryAt = null;
		retryCycleStartedAt = cycleStartedAt;
		if (startedAt == null) startedAt = now;
		status = snapshotFixedAt == null ? DrawRunStatus.PREPARING : DrawRunStatus.RUNNING;
	}

	/** 실패는 입력을 유지한다. 예약이 없으면 운영자 대기다. */
	public void fail(Instant retryAt) {
		if (status == DrawRunStatus.CONFIRMED || status == DrawRunStatus.NO_ENTRIES
				|| status == DrawRunStatus.NO_CANDIDATES) throw new IllegalStateException("종료된 실행은 실패로 변경할 수 없습니다.");
		status = DrawRunStatus.FAILED;
		releaseAttempt();
		this.retryAt = retryAt;
	}

	/** 확정·정상 종료 또는 실패 처리 뒤 소유권과 예약을 해제한다. */
	public void releaseAttempt() {
		attemptId = null;
		leaseUntil = null;
		retryAt = null;
	}
}
