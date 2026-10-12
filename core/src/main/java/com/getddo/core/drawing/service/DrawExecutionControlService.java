package com.getddo.core.drawing.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.getddo.core.audit.domain.AuditLogCommand;
import com.getddo.core.audit.service.AuditLogService;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.repository.*;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.user.repository.UserRepository;
import static com.getddo.core.drawing.exception.DrawingErrorCode.*;

/** 시도 소유권·실패 이력·예약을 실행/선정 롤백과 별도로 확정한다. */
@Service
@RequiredArgsConstructor
public class DrawExecutionControlService {
	private static final long[] RETRY_SECONDS = {10, 30, 60};
	private final DrawResultRepository results;
	private final DrawSnapshotRepository snapshots;
	private final DrawExecutionRepository executions;
	private final DrawFailureRepository failures;
	private final UserRepository users;
	private final AuditLogService audit;
	private final TimeProvider time;

	/** 등록 트랜잭션은 이미 커밋돼 있어야 한다. 같은 실행의 자동·운영자 요청을 직렬화한다. */
	@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
	public DrawExecutionClaim claim(UUID runId, UUID actorId, boolean manual, boolean initialOnly) {
		if (manual || actorId != null) requireAdmin(actorId);
		if (runId == null) throw new BusinessException(INVALID_RUN);
		UUID eventId = results.findEventId(runId).orElseThrow(() -> new BusinessException(INVALID_RUN));
		DrawSnapshotSource.Event event = snapshots.lockEvent(eventId).orElseThrow(() -> new BusinessException(EVENT_NOT_FOUND));
		DrawExecutionState state = lock(runId);
		if (initialOnly && state.getDrawType() != DrawType.INITIAL) throw new BusinessException(INVALID_RUN);
		if (state.isTerminal()) {
			if (manual && state.getStatus() != DrawRunStatus.CONFIRMED) throw new BusinessException(INVALID_RUN);
			return new DrawExecutionClaim(state, false);
		}
		Instant now = time.now().truncatedTo(ChronoUnit.MICROS);
		if (state.getAttemptId() != null) {
			if (state.getLeaseUntil() == null) throw new BusinessException(INVALID_RUN);
			if (!now.isBefore(state.getLeaseUntil())) {
				recordLocked(state, state.getAttemptId(), DrawFailureCode.INTERRUPTED, now);
				return recovered(lock(runId), event, now, manual);
			} else return new DrawExecutionClaim(state, false);
		}
		// 소유권 없는 과거 RUNNING은 자동 재선정하지 않고 운영자 대기로 이동한다.
		if (state.getStatus() == DrawRunStatus.RUNNING) {
			recordLocked(state, attemptId(now), DrawFailureCode.INVALID_RUN, now);
			return recovered(lock(runId), event, now, manual);
		}
		if (state.getStatus() == DrawRunStatus.FAILED && !manual
				&& (state.getRetryAt() == null || now.isBefore(state.getRetryAt()))) {
			return new DrawExecutionClaim(state, false);
		}
		if (manual && state.getStatus() != DrawRunStatus.FAILED) throw new BusinessException(INVALID_RUN);
		if (!executable(event, state.getDrawType(), now)) {
			if (manual) throw new BusinessException(NOT_EXECUTABLE);
			// 실행 불가인 등록 작업을 계속 폴링하지 않는다. FAILED 이력은 유지하고 예약만 해제한다.
			if (state.getStatus() == DrawRunStatus.FAILED) executions.fail(runId, null);
			else recordLocked(state, attemptId(now), DrawFailureCode.NOT_EXECUTABLE, now);
			return new DrawExecutionClaim(lock(runId), false);
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
		return new DrawExecutionClaim(lock(runId), true);
	}

	/** 복구 이후 새 시도를 같은 트랜잭션에서 시작하지 않아 이후 거절이 복구를 롤백하지 않는다. */
	private DrawExecutionClaim recovered(DrawExecutionState state, DrawSnapshotSource.Event event, Instant now, boolean manual) {
		if (!executable(event, state.getDrawType(), now)) {
			executions.fail(state.getRunId(), null);
			return new DrawExecutionClaim(lock(state.getRunId()), false, manual ? NOT_EXECUTABLE : null);
		}
		return new DrawExecutionClaim(state, false);
	}

	private static boolean executable(DrawSnapshotSource.Event event, DrawType type, Instant now) {
		return !event.deleted() && event.status() != EventStatus.CANCELED && event.status() != EventStatus.SCHEDULED
				&& !now.isBefore(event.endsAt().plusSeconds(300))
				&& (type != DrawType.INITIAL || event.status() == EventStatus.OPEN || event.status() == EventStatus.CLOSED);
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

	@Transactional(readOnly = true)
	public List<UUID> findDue() { return executions.findDue(time.now(), 20); }
	private DrawExecutionState lock(UUID id) { return executions.lockRun(id).orElseThrow(() -> new BusinessException(INVALID_RUN)); }
	private void requireAdmin(UUID actorId) {
		if (actorId == null) throw new BusinessException(ADMIN_REQUIRED);
		User user = users.findById(actorId).orElseThrow(() -> new BusinessException(ADMIN_REQUIRED));
		if (user.role() != UserRole.ADMIN || user.status() != UserStatus.ACTIVE) throw new BusinessException(ADMIN_REQUIRED);
	}
	/** UTC 밀리초와 난수를 조합한 UUID v7을 같은 시도의 실패 식별자로 사용한다. */
	private static UUID attemptId(Instant now) {
		UUID random = UUID.randomUUID();
		return new UUID((now.toEpochMilli() << 16) | 0x7000L | (random.getMostSignificantBits() & 0xfffL), random.getLeastSignificantBits());
	}
}
