package com.getddo.core.drawing.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.getddo.core.drawing.domain.AwardCancellation;

/** 업무 트랜잭션과 이벤트 선잠금은 호출 서비스가 관리한다. */
public interface AwardCancellationRepository {
	Optional<AwardCancellation> findById(UUID cancellationId);
	Optional<AwardCancellation> findByDrawResultId(UUID drawResultId);
	Optional<AwardCancellation> findByRequestKey(UUID requestKey);
	List<AwardCancellation> findByReplacementRunId(UUID runId);
	/** 등록된 실행 연결을 포함해 저장한다. 실패 시 호출 서비스의 전체 트랜잭션을 롤백한다. */
	AwardCancellation save(AwardCancellation cancellation);
}
