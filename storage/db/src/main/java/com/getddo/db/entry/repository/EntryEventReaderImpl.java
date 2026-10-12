package com.getddo.db.entry.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

import com.getddo.core.entry.domain.EntryEvent;
import com.getddo.core.entry.repository.EntryEventReader;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.event.domain.MembershipRule;
import com.getddo.db.common.util.UuidBinary;

/**
 * 응모 판단용 이벤트 읽기 구현.
 *
 * <p>이벤트 테이블은 이벤트 도메인이 소유한다. 응모는 판단에 필요한 컬럼만 읽을 뿐이라 이벤트 Entity를 가져다 쓰지 않고,
 * 응모권 지급이 청구 테이블을 읽는 방식({@code GrantSourceRepositoryImpl})처럼 네이티브 SQL로 읽는다. SQL 문장은 고정이고
 * 값은 모두 파라미터로 바인딩한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class EntryEventReaderImpl implements EntryEventReader {

	private static final String SELECT = """
			select title, event_type, weighting_enabled, max_tickets_per_user, membership_rule,
			       starts_at, ends_at, status, deleted_at
			from events where id = :id
			""";

	private final EntityManager entityManager;

	@Override
	public Optional<EntryEvent> find(UUID eventId) {
		return read(eventId, SELECT);
	}

	@Override
	public Optional<EntryEvent> findForShare(UUID eventId) {
		entityManager.flush();
		return read(eventId, SELECT + "for share");
	}

	private Optional<EntryEvent> read(UUID eventId, String sql) {
		@SuppressWarnings("unchecked")
		NativeQuery<Tuple> query = entityManager.createNativeQuery(sql, Tuple.class)
				.unwrap(NativeQuery.class)
				.addScalar("title", String.class)
				.addScalar("event_type", String.class)
				.addScalar("weighting_enabled", Boolean.class)
				.addScalar("max_tickets_per_user", Integer.class)
				.addScalar("membership_rule", String.class)
				.addScalar("starts_at", Instant.class)
				.addScalar("ends_at", Instant.class)
				.addScalar("status", String.class)
				.addScalar("deleted_at", Instant.class);
		query.setParameter("id", UuidBinary.toBytes(eventId));
		List<Tuple> rows = query.getResultList();
		return rows.stream().findFirst().map(row -> new EntryEvent(eventId,
				row.get("title", String.class),
				EventType.valueOf(row.get("event_type", String.class)),
				row.get("weighting_enabled", Boolean.class),
				row.get("max_tickets_per_user", Integer.class),
				MembershipRule.valueOf(row.get("membership_rule", String.class)),
				row.get("starts_at", Instant.class),
				row.get("ends_at", Instant.class),
				EventStatus.valueOf(row.get("status", String.class)),
				row.get("deleted_at", Instant.class) != null));
	}
}
