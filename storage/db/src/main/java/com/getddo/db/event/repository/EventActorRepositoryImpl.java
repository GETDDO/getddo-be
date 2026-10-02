package com.getddo.db.event.repository;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.getddo.core.event.domain.MembershipRule;
import com.getddo.core.event.repository.EventActorRepository;

/** 공통 사용자 모델 도입 전까지 이벤트 요청자의 DB 역할·상태·멤버십을 읽는다. */
@Repository
public class EventActorRepositoryImpl implements EventActorRepository {
	private final JdbcTemplate jdbc;

	public EventActorRepositoryImpl(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Actor> findById(UUID userId) {
		return jdbc.query("SELECT status, role, membership FROM users WHERE id = UNHEX(REPLACE(?, '-', ''))",
				result -> result.next() ? Optional.of(new Actor(
						"ACTIVE".equals(result.getString("status")),
						"ADMIN".equals(result.getString("role")),
						result.getString("membership") == null ? null
								: MembershipRule.valueOf(result.getString("membership").toLowerCase(Locale.ROOT))))
						: Optional.empty(),
				userId.toString());
	}
}
