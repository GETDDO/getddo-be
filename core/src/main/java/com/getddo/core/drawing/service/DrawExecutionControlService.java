package com.getddo.core.drawing.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.audit.domain.AuditLogCommand;
import com.getddo.core.audit.service.AuditLogService;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.exception.ErrorCode;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.DrawExecutionState;
import com.getddo.core.drawing.domain.DrawFailure;
import com.getddo.core.drawing.domain.DrawFailureCode;
import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.domain.DrawSnapshotSource;
import com.getddo.core.drawing.domain.DrawType;
import com.getddo.core.drawing.repository.DrawExecutionRepository;
import com.getddo.core.drawing.repository.DrawFailureRepository;
import com.getddo.core.drawing.repository.DrawResultRepository;
import com.getddo.core.drawing.repository.DrawSnapshotRepository;

import static com.getddo.core.drawing.exception.DrawingErrorCode.*;

/** 같은 실행의 중복 시도를 막고 실패 기록·다음 재시도 시각을 관리한다. 선정 작업과 별도 트랜잭션으로 저장한다. */
@Service
@RequiredArgsConstructor
public class DrawExecutionControlService {
	private static final long[] RETRY_SECONDS = {
		10, 30, 60
	};
	private final DrawResultRepository results;
	private final DrawSnapshotRepository snapshots;
	private final DrawExecutionRepository executions;
	private final DrawFailureRepository failures;
	private final DrawAdminAuthorization admins;
	private final AuditLogService audit;
	private final TimeProvider time;

	/** 등록 트랜잭션은 이미 커밋돼 있어야 한다. 같은 실행의 자동·운영자 요청을 직렬화한다. */
	@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
	public DrawExecutionControlService.Claim claim(UUID runId, UUID actorId, boolean manual, boolean initialOnly) {
		if (manual || actorId != null) requireAdmin(actorId);
		if (runId == null) throw new BusinessException(INVALID_RUN);
		UUID eventId = results.findEventId(runId).orElseThrow(() -> new BusinessException(INVALID_RUN));
		DrawSnapshotSource.Event event = snapshots.lockEvent(eventId).orElseThrow(() -> new BusinessException(EVENT_NOT_FOUND));
		DrawExecutionState state = lock(runId);
		if (initialOnly && state.getDrawType() != DrawType.INITIAL) throw new BusinessException(INVALID_RUN);
		if (state.isTerminal()) {
			if (manual && state.getStatus() != DrawRunStatus.CONFIRMED) throw new BusinessException(INVALID_RUN);
			return new DrawExecutionControlService.Claim(state, false);
		}
		Instant now = time.now().truncatedTo(ChronoUnit.MICROS);
		if (state.getAttemptId() != null) {
			if (state.getLeaseUntil() == null) throw new BusinessException(INVALID_RUN);
			if (!now.isBefore(state.getLeaseUntil())) {
				recordLocked(state, state.getAttemptId(), DrawFailureCode.INTERRUPTED, now);
				return recovered(lock(runId), event, now, manual);
			} else return new DrawExecutionControlService.Claim(state, false);
		}
		// 소유권 없는 과거 RUNNING은 자동 재선정하지 않고 운영자 대기로 이동한다.
		if (state.getStatus() == DrawRunStatus.RUNNING) {
			recordLocked(state, attemptId(now), DrawFailureCode.INVALID_RUN, now);
			return recovered(lock(runId), event, now, manual);
		}
		if (state.getStatus() == DrawRunStatus.FAILED && !manual
				&& (state.getRetryAt() == null || now.isBefore(state.getRetryAt()))) {
			return new DrawExecutionControlService.Claim(state, false);
		}
		if (manual && state.getStatus() != DrawRunStatus.FAILED) throw new BusinessException(INVALID_RUN);
		if (!DrawExecutionEligibility.isExecutable(event, state.getDrawType(), now)) {
			if (manual) throw new BusinessException(NOT_EXECUTABLE);
			// 실행 불가인 등록 작업을 계속 폴링하지 않는다. FAILED 이력은 유지하고 예약만 해제한다.
			if (state.getStatus() == DrawRunStatus.FAILED) executions.fail(runId, null);
			else recordLocked(state, attemptId(now), DrawFailureCode.NOT_EXECUTABLE, now);
			return new DrawExecutionControlService.Claim(lock(runId), false);
		}
		Instant cycle = state.getRetryCycleStartedAt();
		if (manual || cycle == null) {
			cycle = now;
			Optional<DrawFailure> latest = failures.findLatest(runId);
			// DATETIME(6)에서 같은 시각으로 재개해도 이전 구간 실패가 다시 집계되지 않게 한다.
			if (latest.isPresent() && !cycle.isAfter(latest.get().getFailedAt())) {
				cycle = latest.get().getFailedAt().plus(1, ChronoUnit.MICROS);
			}
		}
		UUID attemptId = attemptId(now);
		executions.claim(runId, attemptId, now, now.plusSeconds(300), cycle);
		audit.record(new AuditLogCommand(actorId, manual ? "DRAW_RESUME" : "DRAW_EXECUTE", "DRAW_RUN", runId,
				null, Map.of("status", state.getStatus().name()), Map.of("attemptId", attemptId.toString()), null));
		return new DrawExecutionControlService.Claim(lock(runId), true);
	}

	/** 복구 이후 새 시도를 같은 트랜잭션에서 시작하지 않아 이후 거절이 복구를 롤백하지 않는다. */
	private DrawExecutionControlService.Claim recovered(DrawExecutionState state,
			DrawSnapshotSource.Event event, Instant now, boolean manual) {
		if (!DrawExecutionEligibility.isExecutable(event, state.getDrawType(), now)) {
			executions.fail(state.getRunId(), null);
			return new DrawExecutionControlService.Claim(lock(state.getRunId()), false, manual ? NOT_EXECUTABLE : null);
		}
		return new DrawExecutionControlService.Claim(state, false);
	}

	/** 실제 실행 트랜잭션이 종료·롤백된 뒤 호출한다. 저장 재시도에는 같은 attemptId를 전달한다. */
	@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
	public DrawExecutionState recordFailure(UUID runId, UUID attemptId, DrawFailureCode code, Instant failedAt) {
		UUID eventId = results.findEventId(runId).orElseThrow(() -> new BusinessException(INVALID_RUN));
		snapshots.lockEvent(eventId).orElseThrow(() -> new BusinessException(EVENT_NOT_FOUND));
		DrawExecutionState state = lock(runId);
		if (failures.findById(attemptId).isPresent() || state.isTerminal()
				|| !attemptId.equals(state.getAttemptId())) return state;
		recordLocked(state, attemptId, code, failedAt);
		return lock(runId);
	}

	/** 실행 잠금 아래 같은 시도의 실패를 기록하고 복구 구간 횟수에 따라 재시도를 예약하거나 운영자 대기로 전환한다. */
	private void recordLocked(DrawExecutionState state, UUID attemptId, DrawFailureCode code, Instant failedAt) {
		Instant at = failedAt.truncatedTo(ChronoUnit.MICROS);
		if (state.getRetryCycleStartedAt() != null && at.isBefore(state.getRetryCycleStartedAt())) at = state.getRetryCycleStartedAt();
		failures.append(new DrawFailure(attemptId, state.getRunId(), code.name(), at, null));
		Instant retryAt = null;
		if (code.isRetryable() && state.getRetryCycleStartedAt() != null) {
			long count = failures.countSince(state.getRunId(), state.getRetryCycleStartedAt());
			if (count >= 1 && count <= RETRY_SECONDS.length) retryAt = at.plusSeconds(RETRY_SECONDS[(int) count - 1]);
		}
		executions.fail(state.getRunId(), retryAt);
	}

	/** 등록 후 시작 누락·만기 예약·만료 소유권을 가진 실행을 한 폴링에 최대 20건 조회한다. 운영자 대기는 자동 실행 대상에서 제외한다. */
	@Transactional(readOnly = true)
	public List<UUID> findDue() {
		return executions.findDue(time.now(), 20);
	}

	/** 이미 이벤트를 잠근 호출에서 실행 잠금을 얻고, 실행이 없으면 일관된 도메인 오류로 거절한다. */
	private DrawExecutionState lock(UUID id) {
		return executions.lockRun(id).orElseThrow(() -> new BusinessException(INVALID_RUN));
	}

	/** 저장된 사용자 역할·상태를 확인해 실제 시작·재개 처리자가 활성 관리자인지 검증한다. */
	private void requireAdmin(UUID actorId) {
		admins.requireActiveAdmin(actorId, ADMIN_REQUIRED);
	}

	/** UTC 밀리초와 난수를 조합한 UUID v7을 같은 시도의 실패 식별자로 사용한다. */
	private static UUID attemptId(Instant now) {
		UUID random = UUID.randomUUID();
		return new UUID((now.toEpochMilli() << 16) | 0x7000L | (random.getMostSignificantBits() & 0xfffL), random.getLeastSignificantBits());
	}

	/** 이번 요청이 실제 추첨을 시작할 수 있는지와 현재 저장 상태를 전달한다. */
	@Getter
	public static final class Claim {
		private final DrawExecutionState state;
		private final boolean claimed;
		private final ErrorCode rejectionCode;
		/** 거절 사유 없이 실행 가능 여부와 저장 상태를 전달한다. */
		public Claim(DrawExecutionState state, boolean claimed) {
			this(state, claimed, null);
		}

		/** 복구 기록은 먼저 저장하고, 실행 거절 사유는 호출한 서비스에서 응답하도록 전달한다. */
		public Claim(DrawExecutionState state, boolean claimed, ErrorCode rejectionCode) {
			this.state = Objects.requireNonNull(state, "state");
			if (claimed && rejectionCode != null) throw new IllegalArgumentException("획득한 시도를 거절로 표시할 수 없습니다.");
			this.claimed = claimed;
			this.rejectionCode = rejectionCode;
		}
	}
}
