package com.getddo.db.ticket.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceClaim;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.repository.GrantSourceRepository;

/**
 * 지급 근거 청구 조회 구현.
 *
 * <p>청구 테이블은 미션·출석·게임이 소유하고 아직 Entity가 없으므로, 다른 도메인 Entity를 만들지 않고
 * 네이티브 SQL로 사용자와 수량만 읽는다. 테이블 이름은 청구 종류 enum으로 고른 고정 SQL에만 들어가므로
 * 외부 입력이 SQL 문장에 섞이지 않는다.</p>
 */
@Repository
public class GrantSourceRepositoryImpl implements GrantSourceRepository {

	private static final String MISSION_CLAIM_SQL =
			"select user_id, ticket_count from mission_reward_claims where id = :id";
	private static final String ATTENDANCE_CLAIM_SQL =
			"select user_id, ticket_count from attendance_reward_claims where id = :id";
	private static final String GAME_CLAIM_SQL =
			"select user_id, ticket_count from game_reward_claims where id = :id";

	private final EntityManager entityManager;

	public GrantSourceRepositoryImpl(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>호출자가 같은 트랜잭션에서 JPA로 저장했지만 아직 DB에 쓰지 않은 청구도 보이도록 먼저 flush한다.
	 * 따라서 트랜잭션 안에서만 호출할 수 있다.</p>
	 */
	@Override
	public Optional<GrantSourceClaim> find(GrantSource source) {
		entityManager.flush();
		@SuppressWarnings("unchecked")
		List<Object[]> rows = entityManager.createNativeQuery(claimSql(source.type()))
				.setParameter("id", UuidBinary.toBytes(source.claimId()))
				.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = rows.get(0);
		return Optional.of(new GrantSourceClaim(
				UuidBinary.fromBytes((byte[]) row[0]),
				((Number) row[1]).longValue()));
	}

	private static String claimSql(GrantSourceType type) {
		return switch (type) {
			case MISSION -> MISSION_CLAIM_SQL;
			case ATTENDANCE -> ATTENDANCE_CLAIM_SQL;
			case GAME -> GAME_CLAIM_SQL;
		};
	}
}
