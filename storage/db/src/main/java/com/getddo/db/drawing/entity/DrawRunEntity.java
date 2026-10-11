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
		@UniqueConstraint(name = "uq_draw_runs_event", columnNames = {"id", "event_id"}),
		@UniqueConstraint(name = "uq_draw_runs_1", columnNames = {"event_id", "run_number"})
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

	/** 이벤트의 최초 실행을 PREPARING 상태와 실행 번호 0으로 생성한다. */
	public DrawRunEntity(UUID eventId) {
		this.eventId = eventId;
		runNumber = 0;
		drawType = DrawType.INITIAL;
		status = DrawRunStatus.PREPARING;
	}

	/**
	 * 최초 후보 준비가 끝난 실행의 입력을 한 번만 확정한다. 새 NO_CANDIDATES는 생성하지 않는다.
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
		if (status != DrawRunStatus.READY && status != DrawRunStatus.NO_ENTRIES) {
			throw new IllegalArgumentException("최초 입력 확정 상태가 아닙니다.");
		}
		if (algorithmVersion == null || rulesSnapshot == null || fixedAt == null) {
			throw new IllegalArgumentException("확정 입력은 알고리즘 버전·규칙·확정 시각이 필요합니다.");
		}
		this.status = status;
		this.algorithmVersion = algorithmVersion;
		this.rulesSnapshot = rulesSnapshot;
		snapshotFixedAt = fixedAt;
	}
	/** 결과 저장과 같은 트랜잭션에서만 호출한다. 실패하면 READY와 결과 모두 롤백된다. */
	public void confirm(Instant now) {
		if (status != DrawRunStatus.READY || now == null || snapshotFixedAt == null || now.isBefore(snapshotFixedAt)) {
			throw new IllegalStateException("READY 실행만 결과를 확정할 수 있습니다.");
		}
		startedAt = now;
		confirmedAt = now;
		status = DrawRunStatus.CONFIRMED;
	}

}
