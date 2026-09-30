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
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

import com.getddo.core.ticket.domain.TicketLedgerCursor;
import com.getddo.core.ticket.domain.TicketLedgerFilter;
import com.getddo.core.ticket.domain.TicketTransactionType;
import com.getddo.core.ticket.domain.TicketTransactionView;
import com.getddo.core.ticket.domain.TicketWallet;
import com.getddo.core.ticket.repository.TicketQueryRepository;
import com.getddo.db.ticket.mapper.TicketWalletMapper;

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
public class TicketQueryRepositoryImpl implements TicketQueryRepository {

	private static final String LEDGER_SELECT = """
			select l.id, l.wallet_id, l.transaction_type, l.quantity, l.balance_after, l.reason, l.created_at,
			       l.expires_at, ee.event_id, l.event_entry_id, mc.mission_id, gc.game_id, a.attendance_date,
			       l.related_ledger_id, l.refund_of_id
			from ticket_ledger l
			left join mission_reward_claims mc on mc.id = l.mission_reward_claim_id
			left join game_reward_claims gc on gc.id = l.game_reward_claim_id
			left join attendance_reward_claims ac on ac.id = l.attendance_reward_claim_id
			left join attendances a on a.id = ac.attendance_id
			left join event_entries ee on ee.id = l.event_entry_id
			""";
	private static final String LEDGER_COUNT = "select count(*) from ticket_ledger l";
	private static final String AFTER_CURSOR =
			" and (l.created_at < :cursorAt or (l.created_at = :cursorAt and l.id < :cursorId))";
	private static final String LEDGER_ORDER = " order by l.created_at desc, l.id desc";

	private final EntityManager entityManager;
	private final TicketWalletJpaRepository walletJpaRepository;

	public TicketQueryRepositoryImpl(EntityManager entityManager, TicketWalletJpaRepository walletJpaRepository) {
		this.entityManager = entityManager;
		this.walletJpaRepository = walletJpaRepository;
	}

	@Override
	public List<TicketWallet> findWallets(UUID userId) {
		return walletJpaRepository.findByUserIdOrderByExpiryMonthDesc(userId).stream()
				.map(TicketWalletMapper::toDomain)
				.toList();
	}

	@Override
	public List<TicketTransactionView> findLedger(UUID userId, TicketLedgerFilter filter, TicketLedgerCursor after,
			int limit) {
		Map<String, Object> parameters = new HashMap<>();
		StringBuilder sql = new StringBuilder(LEDGER_SELECT).append(where(userId, filter, parameters));
		if (after != null) {
			sql.append(AFTER_CURSOR);
			parameters.put("cursorAt", after.getCreatedAt());
			parameters.put("cursorId", UuidBinary.toBytes(after.getId()));
		}
		sql.append(LEDGER_ORDER);

		@SuppressWarnings("unchecked")
		NativeQuery<Tuple> query = entityManager.createNativeQuery(sql.toString(), Tuple.class)
				.unwrap(NativeQuery.class)
				.addScalar("id", UUID.class)
				.addScalar("wallet_id", UUID.class)
				.addScalar("transaction_type", String.class)
				.addScalar("quantity", Long.class)
				.addScalar("balance_after", Long.class)
				.addScalar("reason", String.class)
				.addScalar("created_at", Instant.class)
				.addScalar("expires_at", Instant.class)
				.addScalar("event_id", UUID.class)
				.addScalar("event_entry_id", UUID.class)
				.addScalar("mission_id", UUID.class)
				.addScalar("game_id", UUID.class)
				.addScalar("attendance_date", LocalDate.class)
				.addScalar("related_ledger_id", UUID.class)
				.addScalar("refund_of_id", UUID.class);
		bind(query, parameters);
		query.setMaxResults(limit);
		return query.getResultList().stream().map(TicketQueryRepositoryImpl::toView).toList();
	}

	@Override
	public long countLedger(UUID userId, TicketLedgerFilter filter) {
		Map<String, Object> parameters = new HashMap<>();
		Query query = entityManager.createNativeQuery(LEDGER_COUNT + where(userId, filter, parameters));
		bind(query, parameters);
		return ((Number) query.getSingleResult()).longValue();
	}

	/** 사용자와 조회 조건의 WHERE 절을 만들고 바인딩할 값을 채운다. 커서 조건은 포함하지 않는다. */
	private static String where(UUID userId, TicketLedgerFilter filter, Map<String, Object> parameters) {
		StringBuilder where = new StringBuilder(" where l.user_id = :userId");
		parameters.put("userId", UuidBinary.toBytes(userId));
		if (filter.getTransactionType() != null) {
			where.append(" and l.transaction_type = :transactionType");
			parameters.put("transactionType", filter.getTransactionType().name());
		}
		if (filter.getFrom() != null) {
			where.append(" and l.created_at >= :from");
			parameters.put("from", filter.getFrom());
		}
		if (filter.getTo() != null) {
			where.append(" and l.created_at < :to");
			parameters.put("to", filter.getTo());
		}
		return where.toString();
	}

	private static void bind(Query query, Map<String, Object> parameters) {
		parameters.forEach(query::setParameter);
	}

	private static TicketTransactionView toView(Tuple row) {
		return TicketTransactionView.builder()
				.id(row.get("id", UUID.class))
				.walletId(row.get("wallet_id", UUID.class))
				.transactionType(TicketTransactionType.valueOf(row.get("transaction_type", String.class)))
				.quantity(row.get("quantity", Long.class))
				.balanceAfter(row.get("balance_after", Long.class))
				.reason(row.get("reason", String.class))
				.createdAt(row.get("created_at", Instant.class))
				.expiresAt(row.get("expires_at", Instant.class))
				.eventId(row.get("event_id", UUID.class))
				.eventEntryId(row.get("event_entry_id", UUID.class))
				.missionId(row.get("mission_id", UUID.class))
				.gameId(row.get("game_id", UUID.class))
				.attendanceDate(row.get("attendance_date", LocalDate.class))
				.relatedLedgerId(row.get("related_ledger_id", UUID.class))
				.refundOfId(row.get("refund_of_id", UUID.class))
				.build();
	}
}
