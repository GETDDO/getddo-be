package com.getddo.db.event.repository;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.event.domain.EventQueryFilter;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.event.domain.EventView;
import com.getddo.core.event.domain.MembershipRule;
import com.getddo.core.event.domain.RegisteredEvent;
import com.getddo.core.event.repository.EventQueryRepository;

/**
 * 이벤트 조회 전용 구현. UUID·시각은 Hibernate 타입으로 읽어 기존 Entity와 UTC 해석을 맞춘다.
 * 관리자 목록의 경품은 페이지의 이벤트 ID를 모아 한 번에 읽고, 사용자 목록에서는 경품을 읽지 않는다.
 * 추첨 영역의 발표 기록은 공개 상태 판정을 위해 읽기만 한다.
 */
@Repository
public class EventQueryRepositoryImpl implements EventQueryRepository {
	private static final String PUBLIC_STATUS = """
			case when e.status = 'REDRAWING'
				then case when exists (select 1 from publications p where p.event_id = e.id)
					then 'PUBLISHED' else 'DRAW_CONFIRMED' end
				else e.status end
			""";
	private static final String SELECT = """
			select e.id, e.created_by, e.title, e.description, e.image_key, e.event_type,
			       e.weighting_enabled, e.max_tickets_per_user, e.membership_rule,
			       e.starts_at, e.ends_at, e.status, e.created_at, e.updated_at,
			       e.suspended_from_status, e.suspended_at, e.canceled_at,
			""" + PUBLIC_STATUS + " as public_status from events e";
	private final EntityManager entityManager;

	public EventQueryRepositoryImpl(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public PageResult<EventView> findAll(EventQueryFilter filter, PageQuery page, boolean publicView) {
		Map<String, Object> parameters = new HashMap<>();
		String where = where(filter, publicView, parameters);
		Query countQuery = entityManager.createNativeQuery("select count(*) from events e" + where);
		bind(countQuery, parameters);
		long total = ((Number) countQuery.getSingleResult()).longValue();

		NativeQuery<Tuple> query = eventQuery(SELECT + where
				+ " order by e.created_at desc, e.id desc limit :limit offset :offset");
		bind(query, parameters);
		query.setParameter("limit", page.getSize());
		query.setParameter("offset", page.offset());
		List<Tuple> rows = query.getResultList();
		Map<UUID, List<RegisteredEvent.Prize>> prizes = publicView ? Map.of() : findPrizes(rows);
		return new PageResult<>(rows.stream().map(row -> toView(row, prizes)).toList(),
				page.getPage(), page.getSize(), total);
	}

	@Override
	public Optional<EventView> findById(UUID eventId) {
		NativeQuery<Tuple> query = eventQuery(SELECT + " where e.deleted_at is null and e.id = :id");
		query.setParameter("id", uuidBytes(eventId));
		List<Tuple> rows = query.getResultList();
		Map<UUID, List<RegisteredEvent.Prize>> prizes = findPrizes(rows);
		return rows.stream().findFirst().map(row -> toView(row, prizes));
	}

	private static String where(EventQueryFilter filter, boolean publicView, Map<String, Object> parameters) {
		StringBuilder where = new StringBuilder(" where e.deleted_at is null");
		if (filter.getStatus() != null) {
			where.append(" and (").append(publicView ? PUBLIC_STATUS : "e.status").append(") = :status");
			parameters.put("status", filter.getStatus().name());
		}
		if (filter.getEventType() != null) {
			where.append(" and e.event_type = :eventType");
			parameters.put("eventType", filter.getEventType().name());
		}
		if (filter.getMembershipRule() != null) {
			where.append(" and e.membership_rule = :membershipRule");
			parameters.put("membershipRule", filter.getMembershipRule().name());
		}
		if (filter.getKeyword() != null) {
			where.append(" and e.title like :keyword escape '!'");
			parameters.put("keyword", "%" + filter.getKeyword().replace("!", "!!")
					.replace("%", "!%").replace("_", "!_") + "%");
		}
		// 모집 구간 [starts_at, ends_at)과 검색 구간 [from, to)가 한 순간이라도 겹치는 경우.
		if (filter.getFrom() != null) {
			where.append(" and e.ends_at > :from");
			parameters.put("from", filter.getFrom());
		}
		if (filter.getTo() != null) {
			where.append(" and e.starts_at < :to");
			parameters.put("to", filter.getTo());
		}
		return where.toString();
	}

	private Map<UUID, List<RegisteredEvent.Prize>> findPrizes(List<Tuple> events) {
		if (events.isEmpty()) {
			return Map.of();
		}
		@SuppressWarnings("unchecked")
		NativeQuery<Tuple> query = entityManager.createNativeQuery("""
				select p.id, p.event_id, p.prize_rank, p.name, p.description, p.image_key, p.winner_count
				from event_prizes p where p.event_id in (:eventIds) order by p.event_id, p.prize_rank
				""", Tuple.class).unwrap(NativeQuery.class)
				.addScalar("id", UUID.class).addScalar("event_id", UUID.class)
				.addScalar("prize_rank", Integer.class).addScalar("name", String.class)
				.addScalar("description", String.class).addScalar("image_key", String.class)
				.addScalar("winner_count", Integer.class);
		query.setParameterList("eventIds", events.stream().map(row -> uuidBytes(row.get("id", UUID.class))).toList());
		Map<UUID, List<RegisteredEvent.Prize>> prizes = new HashMap<>();
		for (Tuple row : query.getResultList()) {
			prizes.computeIfAbsent(row.get("event_id", UUID.class), ignored -> new ArrayList<>())
					.add(new RegisteredEvent.Prize(row.get("id", UUID.class), row.get("prize_rank", Integer.class),
							row.get("name", String.class), row.get("description", String.class),
							row.get("image_key", String.class), row.get("winner_count", Integer.class)));
		}
		return prizes;
	}

	@SuppressWarnings("unchecked")
	private NativeQuery<Tuple> eventQuery(String sql) {
		return entityManager.createNativeQuery(sql, Tuple.class).unwrap(NativeQuery.class)
				.addScalar("id", UUID.class).addScalar("created_by", UUID.class)
				.addScalar("title", String.class).addScalar("description", String.class)
				.addScalar("image_key", String.class).addScalar("event_type", String.class)
				.addScalar("weighting_enabled", Boolean.class).addScalar("max_tickets_per_user", Integer.class)
				.addScalar("membership_rule", String.class).addScalar("starts_at", Instant.class)
				.addScalar("ends_at", Instant.class).addScalar("status", String.class)
				.addScalar("created_at", Instant.class).addScalar("updated_at", Instant.class)
				.addScalar("suspended_from_status", String.class).addScalar("suspended_at", Instant.class)
				.addScalar("canceled_at", Instant.class).addScalar("public_status", String.class);
	}

	private static EventView toView(Tuple row, Map<UUID, List<RegisteredEvent.Prize>> prizes) {
		UUID id = row.get("id", UUID.class);
		RegisteredEvent details = new RegisteredEvent(id, row.get("created_by", UUID.class),
				row.get("title", String.class), row.get("description", String.class), row.get("image_key", String.class),
				EventType.valueOf(row.get("event_type", String.class)), row.get("weighting_enabled", Boolean.class),
				row.get("max_tickets_per_user", Integer.class), MembershipRule.valueOf(row.get("membership_rule", String.class)),
				row.get("starts_at", Instant.class), row.get("ends_at", Instant.class),
				EventStatus.valueOf(row.get("status", String.class)), row.get("created_at", Instant.class),
				row.get("updated_at", Instant.class), prizes.getOrDefault(id, List.of()));
		String suspendedFrom = row.get("suspended_from_status", String.class);
		return new EventView(details, EventStatus.valueOf(row.get("public_status", String.class)),
				suspendedFrom == null ? null : EventStatus.valueOf(suspendedFrom),
				row.get("suspended_at", Instant.class), row.get("canceled_at", Instant.class));
	}

	private static void bind(Query query, Map<String, Object> parameters) {
		parameters.forEach(query::setParameter);
	}

	private static byte[] uuidBytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
	}
}
