package com.getddo.db.drawing.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.drawing.domain.DrawCheckItem;
import com.getddo.core.drawing.domain.DrawVerification;
import com.getddo.core.drawing.repository.DrawCheckRepository;
import com.getddo.db.drawing.entity.DrawCheckEntity;
import com.getddo.db.drawing.entity.DrawRunEntity;
import com.getddo.db.drawing.mapper.DrawCheckMapper;

/** 검증 이력 저장·조회. 트랜잭션 경계는 검증 서비스가 소유한다. */
@Repository
@RequiredArgsConstructor
public class DrawCheckRepositoryImpl implements DrawCheckRepository {
	private final DrawCheckJpaRepository repository;
	private final DrawCheckMapper mapper;
	private final EntityManager entityManager;

	@Override
	public DrawVerification save(UUID runId, UUID actorId, Instant checkedAt, List<DrawCheckItem> items) {
		DrawRunEntity run = entityManager.getReference(DrawRunEntity.class, runId);
		DrawCheckEntity entity = repository.saveAndFlush(new DrawCheckEntity(run, actorId,
				items.stream().allMatch(DrawCheckItem::isPassed), mapper.snapshot(items), checkedAt));
		entityManager.detach(entity);
		return mapper.toDomain(repository.findById(entity.getId()).orElseThrow());
	}

	@Override
	public PageResult<DrawVerification> findByRun(UUID runId, PageQuery query) {
		Page<DrawCheckEntity> page = repository.findByDrawRunId(runId, PageRequest.of(query.getPage() - 1,
				query.getSize(), Sort.by(Sort.Order.desc("checkedAt"), Sort.Order.desc("id"))));
		return new PageResult<>(page.getContent().stream().map(mapper::toDomain).toList(),
				query.getPage(), query.getSize(), page.getTotalElements());
	}
}
