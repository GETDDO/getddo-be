package com.getddo.core.drawing.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.getddo.core.drawing.domain.DrawExecutionState;

/** 모든 변경은 서비스의 이벤트 → 실행 잠금 아래 같은 트랜잭션에서 수행한다. */
public interface DrawExecutionRepository {
	/** 이벤트 선잠금 이후 실행을 쓰기 잠금으로 읽는다. 같은 실행의 시작·재개·실패 저장을 직렬화한다. */
	Optional<DrawExecutionState> lockRun(UUID runId);
	/** 시도 토큰·임대와 복구 구간을 저장해 소유권을 획득한다. 입력 고정 여부에 따른 상태 전이는 영속 실행 엔티티가 적용한다. */
	void claim(UUID runId, UUID attemptId, Instant now, Instant leaseUntil, Instant cycleStartedAt);
	/** 같은 실행을 FAILED로 전환하며 소유권을 해제한다. 예약 시각이 없으면 운영자 확인 대기로 남긴다. */
	void fail(UUID runId, Instant retryAt);
	/** 같은 실행의 시도 토큰·임대·예약을 해제한다. 실패 이력이나 이미 고정된 입력은 변경하지 않는다. */
	void release(UUID runId);
	/** 등록 뒤 시작하지 못한 실행·만기 예약·만료 소유권을 조회한다. 운영자 대기는 제외한다. */
	List<UUID> findDue(Instant now, int limit);
}
