package com.getddo.core.drawing.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.getddo.core.drawing.domain.DrawFailure;

/** 이력의 집계·저장. 업무 트랜잭션 경계는 호출 서비스가 관리한다. */
public interface DrawFailureRepository {
	/** 실패 ID로 기존 기록을 찾아 같은 시도의 저장 재요청을 식별한다. */
	Optional<DrawFailure> findById(UUID failureId);
	/** 동일 실패 기록의 재요청은 멱등 처리하고 다른 시도의 실패는 새 이력으로 보존한다. */
	void append(DrawFailure failure);
	/** 실행에 연결된 전체 실패 이력 수를 조회한다. 운영자 재개 후에도 누적 이력은 유지한다. */
	long count(UUID runId);
	/** 복구 구간 시작 시각을 포함한 이후 실패 수를 조회해 해당 구간의 자동 재시도 한도를 계산한다. */
	long countSince(UUID runId, Instant cycleStartedAt);
	/** 실행의 가장 최근 실패를 조회해 관리자 확인과 새 복구 구간 경계 계산에 사용한다. */
	Optional<DrawFailure> findLatest(UUID runId);
}
