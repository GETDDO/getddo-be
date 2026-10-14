package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 관리자 추첨 조회에 사용하는 데이터를 실행·후보·결과·처리 이력별로 모은다. */
public final class DrawInspectionView {
	private DrawInspectionView() {
	}

	/** 관리자 목록·검증에서 사용하는 실행 종류·상태·저장 조건과 시각이다. */
	@Getter
	public static final class Run {
		private final UUID id;
		private final UUID eventId;
		private final int runNumber;
		private final DrawType drawType;
		private final DrawRunStatus status;
		private final UUID originalDrawId;
		private final String algorithmVersion;
		private final DrawSnapshot.Rules rules;
		private final DrawReplacementRulesView replacementRules;
		private final Instant snapshotFixedAt;
		private final Instant startedAt;
		private final Instant confirmedAt;
		private final Instant createdAt;

		public Run(UUID id, UUID eventId, int runNumber, DrawType drawType, DrawRunStatus status,
				UUID originalDrawId, String algorithmVersion, DrawSnapshot.Rules rules, Instant snapshotFixedAt,
				Instant startedAt, Instant confirmedAt, Instant createdAt) {
			this(id, eventId, runNumber, drawType, status, originalDrawId, algorithmVersion, rules,
					snapshotFixedAt, startedAt, confirmedAt, createdAt, null);
		}

		public Run(UUID id, UUID eventId, int runNumber, DrawType drawType, DrawRunStatus status,
				UUID originalDrawId, String algorithmVersion, DrawSnapshot.Rules rules, Instant snapshotFixedAt,
				Instant startedAt, Instant confirmedAt, Instant createdAt, DrawReplacementRulesView replacementRules) {
			this.id = id;
			this.eventId = eventId;
			this.runNumber = runNumber;
			this.drawType = drawType;
			this.status = status;
			this.originalDrawId = originalDrawId;
			this.algorithmVersion = algorithmVersion;
			this.rules = rules;
			this.replacementRules = replacementRules;
			this.snapshotFixedAt = snapshotFixedAt;
			this.startedAt = startedAt;
			this.confirmedAt = confirmedAt;
			this.createdAt = createdAt;
		}
	}

	/** 실행 정보와 결과 목록, 최근 실패·재시도 요약을 함께 전달한다. */
	@Getter
	public static final class Detail {
		private final DrawInspectionView.Run run;
		private final List<DrawInspectionView.Result> results;
		private final DrawInspectionView.ExecutionSummary execution;
		/** 같은 조회 단위의 실행과 결과를 보존한다. 연결하지 않은 운영 요약은 null로 유지한다. */
		public Detail(DrawInspectionView.Run run, List<DrawInspectionView.Result> results) {
			this(run, results, null);
		}

		/** 같은 조회 단위의 실행과 결과를 보존한다. 연결하지 않은 운영 요약은 null로 유지한다. */
		public Detail(DrawInspectionView.Run run, List<DrawInspectionView.Result> results,
				DrawInspectionView.ExecutionSummary execution) {
			this.execution = execution;
			this.run = run;
			this.results = List.copyOf(results);
		}
	}

	/** 저장된 후보와 원본 실행·이벤트·응모자 연결을 전달한다. 연결 관계 검사에도 사용한다. */
	@Getter
	public static final class Candidate {
		private final DrawSnapshot.Candidate snapshot;
		private final UUID sourceRunId;
		private final UUID sourceEventId;
		private final UUID participantEventId;
		private final UUID participantUserId;

		public Candidate(DrawSnapshot.Candidate snapshot, UUID sourceRunId, UUID sourceEventId,
				UUID participantEventId, UUID participantUserId) {
			this.snapshot = snapshot;
			this.sourceRunId = sourceRunId;
			this.sourceEventId = sourceEventId;
			this.participantEventId = participantEventId;
			this.participantUserId = participantUserId;
		}
	}

	/** 저장된 선정·미충원 결과와 공개 이력 여부·당첨 취소 여부를 전달한다. */
	@Getter
	public static final class Result {
		private final UUID id;
		private final UUID runId;
		private final UUID prizeId;
		private final UUID prizeEventId;
		private final int slotNumber;
		private final Integer selectionOrder;
		private final DrawSelectionResultType resultType;
		private final UUID candidateId;
		private final UUID userId;
		private final boolean wasPublished;
		private final boolean canceled;

		public Result(UUID id, UUID runId, UUID prizeId, UUID prizeEventId, int slotNumber,
				Integer selectionOrder, DrawSelectionResultType resultType, UUID candidateId,
				boolean wasPublished, boolean canceled) {
			this(id, runId, prizeId, prizeEventId, slotNumber, selectionOrder, resultType,
					candidateId, null, wasPublished, canceled);
		}

		public Result(UUID id, UUID runId, UUID prizeId, UUID prizeEventId, int slotNumber,
				Integer selectionOrder, DrawSelectionResultType resultType, UUID candidateId,
				UUID userId, boolean wasPublished, boolean canceled) {
			this.id = id;
			this.runId = runId;
			this.prizeId = prizeId;
			this.prizeEventId = prizeEventId;
			this.slotNumber = slotNumber;
			this.selectionOrder = selectionOrder;
			this.resultType = resultType;
			this.candidateId = candidateId;
			this.userId = userId;
			this.wasPublished = wasPublished;
			this.canceled = canceled;
		}
	}

	/** 최근 실패·전체 실패 횟수·재시도 예약·관리자 대기·취소 정보를 요약한다. */
	@Getter
	@EqualsAndHashCode
	public static final class ExecutionSummary {
		private final long failureCount;
		private final DrawFailure latestFailure;
		private final Instant retryAt;
		private final boolean waitingForOperator;
		private final List<DrawInspectionView.Cancellation> cancellations;

		/** 저장된 조회 필드를 보존하고 컬렉션은 외부 변경으로부터 보호한다. */
		public ExecutionSummary(long failureCount, DrawFailure latestFailure, Instant retryAt,
				boolean waitingForOperator, List<DrawInspectionView.Cancellation> cancellations) {
			this.failureCount = failureCount;
			this.latestFailure = latestFailure;
			this.retryAt = retryAt;
			this.waitingForOperator = waitingForOperator;
			this.cancellations = List.copyOf(Objects.requireNonNull(cancellations, "cancellations"));
		}
	}

	/** 당첨 취소 사유·처리자·시각과 연결된 재추첨 실행을 전달한다. */
	@Getter
	@EqualsAndHashCode
	public static final class Cancellation {
		private final UUID id;
		private final UUID resultId;
		private final UUID replacementRunId;
		private final UUID canceledBy;
		private final String reason;
		private final Instant canceledAt;

		/** 결과의 취소와 후속 재추첨의 연결을 원본 조회값대로 보존한다. */
		public Cancellation(UUID id, UUID resultId, UUID replacementRunId, UUID canceledBy,
				String reason, Instant canceledAt) {
			this.id = id;
			this.resultId = resultId;
			this.replacementRunId = replacementRunId;
			this.canceledBy = canceledBy;
			this.reason = reason;
			this.canceledAt = canceledAt;
		}
	}

	/** 실행 시작·재개 등의 처리자와 원본 감사 기록을 관리자 조회에 전달한다. */
	@Getter
	@EqualsAndHashCode
	public static final class Audit {
		private final UUID id;
		private final UUID actorId;
		private final String action;
		private final String reason;
		private final String beforeData;
		private final String afterData;
		private final String requestId;
		private final Instant createdAt;

		/** 실제 실행자와 원본 감사 내용을 새 업무 판단 없이 보존한다. */
		public Audit(UUID id, UUID actorId, String action, String reason, String beforeData,
				String afterData, String requestId, Instant createdAt) {
			this.id = id;
			this.actorId = actorId;
			this.action = action;
			this.reason = reason;
			this.beforeData = beforeData;
			this.afterData = afterData;
			this.requestId = requestId;
			this.createdAt = createdAt;
		}
	}
}
