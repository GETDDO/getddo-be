package com.getddo.db.ticket.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketHistoryCursor;
import com.getddo.core.ticket.domain.TicketHistoryFilter;
import com.getddo.core.ticket.domain.TicketHistoryView;
import com.getddo.core.ticket.domain.TicketHolding;
import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.ticket.repository.TicketQueryRepository;
import com.getddo.db.common.util.UuidBinary;

/**
 * 사용자 화면용 응모권 조회 구현.
 *
 * <p>이력의 미션·게임·출석일·이벤트는 다른 도메인이 소유한 청구·응모 테이블에서 가져온다. 해당 Entity를 만들지 않고
 * 네이티브 SQL의 LEFT JOIN으로 읽는다. SQL 문장은 고정 조각만 이어 붙이고 값은 모두 파라미터로 바인딩한다.</p>
 *
 * <p>결과 컬럼은 Entity와 같은 Hibernate 타입(UUID, Instant)으로 읽어 UUID 바이트 순서와 UTC 해석이 Entity 조회와
 * 같게 한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class TicketQueryRepositoryImpl implements TicketQueryRepository {

	private static final String HOLDINGS_SELECT = """
			select t.grade, t.expires_at, count(*) as ticket_count
			from tickets t
			where t.user_id = :userId and t.status in ('AVAILABLE', 'RETURNED') and t.expires_at > :now
			group by t.grade, t.expires_at
			order by t.expires_at asc, t.grade asc
			""";
	private static final String HISTORY_SELECT = """
			select h.id, h.ticket_id, h.operation_type, t.grade, h.status, h.expires_at, h.reason, h.created_at,
			       ep.event_id, h.event_entry_id, mc.mission_id, gc.game_id, a.attendance_date,
			       h.original_use_history_id, h.corrected_history_id
			from ticket_histories h
			join tickets t on t.id = h.ticket_id
			left join mission_reward_claims mc on mc.id = t.mission_reward_claim_id
			left join game_reward_claims gc on gc.id = t.game_reward_claim_id
			left join attendance_reward_claims ac on ac.id = t.attendance_reward_claim_id
			left join attendances a on a.id = ac.attendance_id
			left join event_entries ee on ee.id = h.event_entry_id
			left join event_participants ep on ep.id = ee.participant_id
			""";
	private static final String HISTORY_COUNT =
			"select count(*) from ticket_histories h join tickets t on t.id = h.ticket_id";
	private static final String AFTER_CURSOR =
			" and (h.created_at < :cursorAt or (h.created_at = :cursorAt and h.id < :cursorId))";
	private static final String HISTORY_ORDER = " order by h.created_at desc, h.id desc";

	private final EntityManager entityManager;

	@Override
	public List<TicketHolding> findHoldings(UUID userId, Instant now) {
		@SuppressWarnings("unchecked")
		NativeQuery<Tuple> query = entityManager.createNativeQuery(HOLDINGS_SELECT, Tuple.class)
				.unwrap(NativeQuery.class)
				.addScalar("grade", String.class)
				.addScalar("expires_at", Instant.class)
				.addScalar("ticket_count", Long.class);
		query.setParameter("userId", UuidBinary.toBytes(userId));
		query.setParameter("now", now);
		return query.getResultList().stream()
				.map(row -> new TicketHolding(
						TicketGrade.valueOf(row.get("grade", String.class)),
						row.get("expires_at", Instant.class),
						row.get("ticket_count", Long.class)))
				.toList();
	}

	@Override
	public List<TicketHistoryView> findHistory(UUID userId, TicketHistoryFilter filter, TicketHistoryCursor after,
			int limit) {
		Map<String, Object> parameters = new HashMap<>();
		StringBuilder sql = new StringBuilder(HISTORY_SELECT).append(where(userId, filter, parameters));
		if (after != null) {
			sql.append(AFTER_CURSOR);
			parameters.put("cursorAt", after.getCreatedAt());
			parameters.put("cursorId", UuidBinary.toBytes(after.getId()));
		}
		sql.append(HISTORY_ORDER);

		@SuppressWarnings("unchecked")
		NativeQuery<Tuple> query = entityManager.createNativeQuery(sql.toString(), Tuple.class)
				.unwrap(NativeQuery.class)
				.addScalar("id", UUID.class)
				.addScalar("ticket_id", UUID.class)
				.addScalar("operation_type", String.class)
				.addScalar("grade", String.class)
				.addScalar("status", String.class)
				.addScalar("expires_at", Instant.class)
				.addScalar("reason", String.class)
				.addScalar("created_at", Instant.class)
				.addScalar("event_id", UUID.class)
				.addScalar("event_entry_id", UUID.class)
				.addScalar("mission_id", UUID.class)
				.addScalar("game_id", UUID.class)
				.addScalar("attendance_date", LocalDate.class)
				.addScalar("original_use_history_id", UUID.class)
				.addScalar("corrected_history_id", UUID.class);
		bind(query, parameters);
		query.setMaxResults(limit);
		return query.getResultList().stream().map(TicketQueryRepositoryImpl::toView).toList();
	}

	@Override
	public long countHistory(UUID userId, TicketHistoryFilter filter) {
		Map<String, Object> parameters = new HashMap<>();
		Query query = entityManager.createNativeQuery(HISTORY_COUNT + where(userId, filter, parameters));
		bind(query, parameters);
		return ((Number) query.getSingleResult()).longValue();
	}

	/** 사용자와 조회 조건의 WHERE 절을 만들고 바인딩할 값을 채운다. 커서 조건은 포함하지 않는다. */
	private static String where(UUID userId, TicketHistoryFilter filter, Map<String, Object> parameters) {
		StringBuilder where = new StringBuilder(" where t.user_id = :userId");
		parameters.put("userId", UuidBinary.toBytes(userId));
		if (filter.getOperationType() != null) {
			where.append(" and h.operation_type = :operationType");
			parameters.put("operationType", filter.getOperationType().name());
		}
		if (filter.getFrom() != null) {
			where.append(" and h.created_at >= :from");
			parameters.put("from", filter.getFrom());
		}
		if (filter.getTo() != null) {
			where.append(" and h.created_at < :to");
			parameters.put("to", filter.getTo());
		}
		return where.toString();
	}

	private static void bind(Query query, Map<String, Object> parameters) {
		parameters.forEach(query::setParameter);
	}

	private static TicketHistoryView toView(Tuple row) {
		return new TicketHistoryView(
				row.get("id", UUID.class),
				row.get("ticket_id", UUID.class),
				TicketOperationType.valueOf(row.get("operation_type", String.class)),
				TicketGrade.valueOf(row.get("grade", String.class)),
				TicketStatus.valueOf(row.get("status", String.class)),
				row.get("expires_at", Instant.class),
				row.get("reason", String.class),
				row.get("created_at", Instant.class),
				row.get("event_id", UUID.class),
				row.get("event_entry_id", UUID.class),
				row.get("mission_id", UUID.class),
				row.get("game_id", UUID.class),
				row.get("attendance_date", LocalDate.class),
				row.get("original_use_history_id", UUID.class),
				row.get("corrected_history_id", UUID.class));
	}
}
