package com.getddo.db.drawing.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.DrawExecutionState;
import com.getddo.core.drawing.repository.DrawExecutionRepository;
import com.getddo.db.drawing.entity.DrawRunEntity;

import static com.getddo.core.drawing.exception.DrawingErrorCode.INVALID_RUN;

/** 제어 상태는 기존 DrawRunEntity에만 저장한다. 업무 트랜잭션은 서비스 책임이다. */
@Repository
@RequiredArgsConstructor
public class DrawExecutionRepositoryImpl implements DrawExecutionRepository {
	private final DrawRunJpaRepository runs;
	/** 서비스의 이벤트 선잠금 이후 실행을 쓰기 잠금으로 조회해 제어 상태를 복원한다. */
	@Override public Optional<DrawExecutionState> lockRun(UUID id) {
		return runs.findForUpdate(id).map(this::state);
	}

	/** 실행 행의 고정 입력 시각·소유권·예약·복구 구간을 공통 상태 모델로 복원한다. */
	private DrawExecutionState state(DrawRunEntity run) {
		return new DrawExecutionState(run.getId(), run.getEventId(), run.getDrawType(), run.getStatus(),
				run.getSnapshotFixedAt(), run.getAttemptId(), run.getLeaseUntil(), run.getRetryAt(), run.getRetryCycleStartedAt());
	}

	/** 호출자의 잠금·트랜잭션 안에서 변경할 실행 엔티티를 읽고 없는 실행은 도메인 오류로 거절한다. */
	private DrawRunEntity run(UUID id) {
		return runs.findById(id).orElseThrow(() -> new BusinessException(INVALID_RUN));
	}

	/** 실행 엔티티에 시도 소유권·임대·복구 구간을 적용하고 같은 서비스 트랜잭션에서 flush한다. */
	@Override public void claim(UUID id, UUID attemptId, Instant now, Instant until, Instant cycle) {
		run(id).claim(attemptId, now, until, cycle);
		runs.flush();
	}

	/** 고정 입력을 유지한 채 실패 상태와 선택적인 재시도 예약을 저장한다. */
	@Override public void fail(UUID id, Instant retryAt) {
		run(id).fail(retryAt);
		runs.flush();
	}

	/** 실행의 처리 소유권과 예약을 해제하고 flush한다. 트랜잭션의 커밋 경계는 서비스가 관리한다. */
	@Override public void release(UUID id) {
		run(id).releaseAttempt();
		runs.flush();
	}

	/** 현재 시각 기준의 등록 미시작·예약·중단 복구 대상을 지정된 건수로 제한해 조회한다. */
	@Override public List<UUID> findDue(Instant now, int limit) {
		return runs.findDue(now, PageRequest.of(0, limit));
	}
}
