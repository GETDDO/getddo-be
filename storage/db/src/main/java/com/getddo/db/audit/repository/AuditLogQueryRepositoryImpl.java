package com.getddo.db.audit.repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.audit.domain.AuditLogEntry;
import com.getddo.core.audit.domain.AuditLogQuery;
import com.getddo.core.audit.repository.AuditLogQueryRepository;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.db.audit.entity.AuditLogEntity;
import com.getddo.db.audit.mapper.AuditLogQueryMapper;
import com.getddo.db.common.pagination.PageOffsets;

/** 동일 조건으로 감사 건수·목록을 읽고 기존 기록 Entity를 변경하지 않는다. */
@Repository
@RequiredArgsConstructor
public class AuditLogQueryRepositoryImpl implements AuditLogQueryRepository {
	private final EntityManager entityManager;
	private final AuditLogQueryMapper mapper;

	@Override
	public PageResult<AuditLogEntry> findAll(AuditLogQuery query) {
		CriteriaBuilder builder = entityManager.getCriteriaBuilder();
		CriteriaQuery<Long> count = builder.createQuery(Long.class);
		Root<AuditLogEntity> countRoot = count.from(AuditLogEntity.class);
		count.select(builder.count(countRoot)).where(predicates(query, builder, countRoot));
		long total = entityManager.createQuery(count).getSingleResult();
		PageQuery page = query.getPage();
		if (PageOffsets.exceedsIntRange(page) || page.offset() >= total) {
			return new PageResult<>(List.of(), page.getPage(), page.getSize(), total);
		}
		CriteriaQuery<AuditLogEntity> select = builder.createQuery(AuditLogEntity.class);
		Root<AuditLogEntity> row = select.from(AuditLogEntity.class);
		select.select(row).where(predicates(query, builder, row))
				.orderBy(builder.desc(row.get("createdAt")), builder.desc(row.get("id")));
		List<AuditLogEntry> items = entityManager.createQuery(select).setFirstResult((int) page.offset())
				.setMaxResults(page.getSize()).getResultList().stream().map(mapper::toDomain).toList();
		return new PageResult<>(items, page.getPage(), page.getSize(), total);
	}

	@Override
	public Optional<AuditLogEntry> findById(UUID id) {
		return Optional.ofNullable(entityManager.find(AuditLogEntity.class, id)).map(mapper::toDomain);
	}

	private static Predicate[] predicates(AuditLogQuery query, CriteriaBuilder builder, Root<AuditLogEntity> row) {
		List<Predicate> predicates = new ArrayList<>();
		if (query.getActorId() != null) predicates.add(builder.equal(row.get("actorId"), query.getActorId()));
		if (query.getAction() != null) predicates.add(builder.equal(row.get("action"), query.getAction()));
		if (query.getTargetType() != null) predicates.add(builder.equal(row.get("targetType"), query.getTargetType()));
		if (query.getTargetId() != null) predicates.add(builder.equal(row.get("targetId"), query.getTargetId()));
		if (query.getFrom() != null) predicates.add(builder.greaterThanOrEqualTo(row.get("createdAt"), ceilMicros(query.getFrom())));
		if (query.getTo() != null) predicates.add(builder.lessThan(row.get("createdAt"), ceilMicros(query.getTo())));
		return predicates.toArray(Predicate[]::new);
	}

	/** DB 행은 마이크로초 격자에 있으므로 양쪽 경계를 올려 나노초 입력의 [from,to) 의미를 보존한다. */
	private static Instant ceilMicros(Instant value) {
		Instant floor = value.truncatedTo(ChronoUnit.MICROS);
		return value.equals(floor) ? floor : floor.plus(1, ChronoUnit.MICROS);
	}
}
