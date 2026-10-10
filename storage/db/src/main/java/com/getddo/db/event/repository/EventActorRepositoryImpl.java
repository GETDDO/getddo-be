package com.getddo.db.event.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.getddo.core.event.repository.EventActorRepository;

/** 이벤트 등록 권한 검증에 필요한 요청자의 DB 역할·상태를 읽는다. */
@Repository
public class EventActorRepositoryImpl implements EventActorRepository {
	private final JdbcTemplate jdbc;

	public EventActorRepositoryImpl(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Actor> findById(UUID userId) {
		return jdbc.query("SELECT status, role FROM users WHERE id = UNHEX(REPLACE(?, '-', ''))",
				result -> result.next() ? Optional.of(new Actor(
						"ACTIVE".equals(result.getString("status")),
						"ADMIN".equals(result.getString("role"))))
						: Optional.empty(),
				userId.toString());
	}
}
