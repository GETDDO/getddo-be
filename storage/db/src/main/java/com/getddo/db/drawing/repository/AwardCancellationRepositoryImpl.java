package com.getddo.db.drawing.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.drawing.domain.AwardCancellation;
import com.getddo.core.drawing.repository.AwardCancellationRepository;
import com.getddo.db.drawing.entity.AwardCancellationEntity;
import com.getddo.db.drawing.mapper.AwardCancellationMapper;

@Repository
@RequiredArgsConstructor
public class AwardCancellationRepositoryImpl implements AwardCancellationRepository {
	private final AwardCancellationJpaRepository cancellations;
	private final AwardCancellationMapper mapper;
	private final EntityManager entityManager;

	@Override
	public Optional<AwardCancellation> findById(UUID cancellationId) {
		return cancellations.findById(cancellationId).map(mapper::toDomain);
	}

	@Override
	public Optional<AwardCancellation> findByDrawResultId(UUID drawResultId) {
		return cancellations.findByDrawResultId(drawResultId).map(mapper::toDomain);
	}

	@Override
	public Optional<AwardCancellation> findByRequestKey(UUID requestKey) {
		return cancellations.findByRequestKey(requestKey).map(mapper::toDomain);
	}

	@Override
	public List<AwardCancellation> findByReplacementRunId(UUID runId) {
		return cancellations.findByReplacementRunId(runId).stream().map(mapper::toDomain).toList();
	}

	@Override
	public AwardCancellation save(AwardCancellation cancellation) {
		// 불변 이력은 merge로 덮어쓰지 않고 INSERT만 수행한다.
		AwardCancellationEntity entity = new AwardCancellationEntity(cancellation);
		entityManager.persist(entity);
		entityManager.flush();
		entityManager.detach(entity);
		return findById(cancellation.id()).orElseThrow();
	}
}
