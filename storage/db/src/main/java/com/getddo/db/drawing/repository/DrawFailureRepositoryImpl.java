package com.getddo.db.drawing.repository;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
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
	/** 실패 ID의 저장 여부와 기존 내용을 도메인 모델로 조회한다. */
	@Override public Optional<DrawFailure> findById(UUID id) {
		return failures.findById(id).map(mapper::toDomain);
	}

	/** 실행별 전체 실패 이력을 집계하며 별도 실행 행에 집계 값을 저장하지 않는다. */
	@Override public long count(UUID runId) {
		return failures.countByRunId(runId);
	}

	/** 새 복구 구간의 경계 시각 이후 실패 수를 포함 조건으로 집계한다. */
	@Override public long countSince(UUID runId, Instant since) {
		return failures.countByRunIdAndFailedAtGreaterThanEqual(runId, since);
	}

	/** 가장 최근 실패를 복원해 관리자 확인과 복구 구간 경계 계산에 제공한다. */
	@Override public Optional<DrawFailure> findLatest(UUID runId) {
		return failures.findFirstByRunIdOrderByFailedAtDescIdDesc(runId).map(mapper::toDomain);
	}

	/** 이벤트 선잠금을 가진 호출에서 실패 행을 추가한다. 동일 ID·내용은 재사용하고 서로 다른 내용의 덮어쓰기는 거절한다. */
	@Override public void append(DrawFailure failure) {
		Optional<DrawFailure> existing = findById(failure.getId());
		if (existing.isPresent()) {
			DrawFailure stored = existing.get();
			if (!stored.getRunId().equals(failure.getRunId()) || !stored.getFailureCode().equals(failure.getFailureCode())
					|| !stored.getFailedAt().equals(failure.getFailedAt()) || !Objects.equals(stored.getTraceId(),
					failure.getTraceId())) {
				throw new BusinessException(INVALID_RUN);
			}
			return;
		}
		entityManager.persist(new DrawFailureEntity(failure));
		entityManager.flush();
	}
}
