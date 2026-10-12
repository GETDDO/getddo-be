package com.getddo.db.drawing.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.DrawFailure;
import com.getddo.core.drawing.repository.DrawFailureRepository;
import com.getddo.db.drawing.entity.DrawFailureEntity;
import com.getddo.db.drawing.mapper.DrawFailureMapper;
import static com.getddo.core.drawing.exception.DrawingErrorCode.INVALID_RUN;

/** 호출자는 이벤트 선잠금으로 같은 실행의 실패 저장을 직렬화한다. */
@Repository
@RequiredArgsConstructor
public class DrawFailureRepositoryImpl implements DrawFailureRepository {
	private final DrawFailureJpaRepository failures;
	private final DrawFailureMapper mapper;
	private final EntityManager entityManager;
	@Override public Optional<DrawFailure> findById(UUID id) { return failures.findById(id).map(mapper::toDomain); }
	@Override public long count(UUID runId) { return failures.countByRunId(runId); }
	@Override public long countSince(UUID runId, Instant since) {
		return failures.countByRunIdAndFailedAtGreaterThanEqual(runId, since);
	}
	@Override public Optional<DrawFailure> findLatest(UUID runId) {
		return failures.findFirstByRunIdOrderByFailedAtDescIdDesc(runId).map(mapper::toDomain);
	}
	@Override public void append(DrawFailure failure) {
		Optional<DrawFailure> existing = findById(failure.getId());
		if (existing.isPresent()) {
			DrawFailure stored = existing.get();
			if (!stored.getRunId().equals(failure.getRunId()) || !stored.getFailureCode().equals(failure.getFailureCode())
					|| !stored.getFailedAt().equals(failure.getFailedAt()) || !Objects.equals(stored.getTraceId(), failure.getTraceId())) {
				throw new BusinessException(INVALID_RUN);
			}
			return;
		}
		entityManager.persist(new DrawFailureEntity(failure));
		entityManager.flush();
	}
}
