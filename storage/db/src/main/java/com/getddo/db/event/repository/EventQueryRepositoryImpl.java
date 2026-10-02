package com.getddo.db.event.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
import com.getddo.db.common.util.UuidBinary;

/**
 * 이벤트 조회 전용 JDBC 구현. BINARY(16)은 공통 UUID 변환을 사용하고 DATETIME(6)은 UTC로 해석한다.
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
	private final NamedParameterJdbcTemplate jdbc;

	public EventQueryRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public PageResult<EventView> findAll(EventQueryFilter filter, PageQuery page, boolean publicView) {
		Map<String, Object> parameters = new HashMap<>();
		String where = where(filter, publicView, parameters);
		long total = jdbc.queryForObject("select count(*) from events e" + where, parameters, Long.class);
		parameters.put("limit", page.getSize());
		parameters.put("offset", page.offset());
		List<EventView> rows = jdbc.query(SELECT + where
				+ " order by e.created_at desc, e.id desc limit :limit offset :offset",
				parameters, (row, rowNumber) -> toView(row));
		return new PageResult<>(publicView ? rows : attachPrizes(rows),
				page.getPage(), page.getSize(), total);
	}

	@Override
	public Optional<EventView> findById(UUID eventId) {
		List<EventView> rows = jdbc.query(SELECT + " where e.deleted_at is null and e.id = :id",
				Map.of("id", UuidBinary.toBytes(eventId)), (row, rowNumber) -> toView(row));
		return attachPrizes(rows).stream().findFirst();
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
			parameters.put("from", LocalDateTime.ofInstant(filter.getFrom(), ZoneOffset.UTC));
		}
		if (filter.getTo() != null) {
			where.append(" and e.starts_at < :to");
			parameters.put("to", LocalDateTime.ofInstant(filter.getTo(), ZoneOffset.UTC));
		}
		return where.toString();
	}

	private List<EventView> attachPrizes(List<EventView> events) {
		Map<UUID, List<RegisteredEvent.Prize>> prizes = findPrizes(events);
		return events.stream().map(event -> withPrizes(event,
				prizes.getOrDefault(event.getDetails().getId(), List.of()))).toList();
	}

	private Map<UUID, List<RegisteredEvent.Prize>> findPrizes(List<EventView> events) {
		if (events.isEmpty()) {
			return Map.of();
		}
		Map<UUID, List<RegisteredEvent.Prize>> prizes = new HashMap<>();
		jdbc.query("""
				select p.id, p.event_id, p.prize_rank, p.name, p.description, p.image_key, p.winner_count
				from event_prizes p where p.event_id in (:eventIds) order by p.event_id, p.prize_rank
				""", Map.of("eventIds", events.stream()
						.map(event -> UuidBinary.toBytes(event.getDetails().getId())).toList()), row -> {
			prizes.computeIfAbsent(UuidBinary.fromBytes(row.getBytes("event_id")), ignored -> new ArrayList<>())
					.add(new RegisteredEvent.Prize(UuidBinary.fromBytes(row.getBytes("id")), row.getInt("prize_rank"),
							row.getString("name"), row.getString("description"),
							row.getString("image_key"), row.getInt("winner_count")));
		});
		return prizes;
	}

	private static EventView toView(ResultSet row) throws SQLException {
		RegisteredEvent details = new RegisteredEvent(UuidBinary.fromBytes(row.getBytes("id")),
				UuidBinary.fromBytes(row.getBytes("created_by")), row.getString("title"),
				row.getString("description"), row.getString("image_key"),
				EventType.valueOf(row.getString("event_type")), row.getBoolean("weighting_enabled"),
				row.getObject("max_tickets_per_user", Integer.class), MembershipRule.valueOf(row.getString("membership_rule")),
				instant(row, "starts_at"), instant(row, "ends_at"), EventStatus.valueOf(row.getString("status")),
				instant(row, "created_at"), instant(row, "updated_at"), List.of());
		String suspendedFrom = row.getString("suspended_from_status");
		return new EventView(details, EventStatus.valueOf(row.getString("public_status")),
				suspendedFrom == null ? null : EventStatus.valueOf(suspendedFrom),
				instant(row, "suspended_at"), instant(row, "canceled_at"));
	}

	private static EventView withPrizes(EventView view, List<RegisteredEvent.Prize> prizes) {
		RegisteredEvent event = view.getDetails();
		RegisteredEvent details = new RegisteredEvent(event.getId(), event.getCreatedBy(), event.getTitle(),
				event.getDescription(), event.getImageKey(), event.getEventType(), event.isWeightingEnabled(),
				event.getMaxTicketsPerUser(), event.getMembershipRule(), event.getStartsAt(), event.getEndsAt(),
				event.getStatus(), event.getCreatedAt(), event.getUpdatedAt(), prizes);
		return new EventView(details, view.getPublicStatus(), view.getSuspendedFromStatus(),
				view.getSuspendedAt(), view.getCanceledAt());
	}

	/** DATETIME에는 시간대가 없으므로 JDBC 기본 시간대에 의존하지 않고 UTC로 해석한다. */
	private static Instant instant(ResultSet row, String column) throws SQLException {
		LocalDateTime value = row.getObject(column, LocalDateTime.class);
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}
}
