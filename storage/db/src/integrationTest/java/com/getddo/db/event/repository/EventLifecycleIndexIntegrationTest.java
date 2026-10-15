package com.getddo.db.event.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.getddo.db.common.util.UuidBinary;
import com.getddo.db.support.MySqlTestContainers;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 후보 SQL이 대부분 미래인 데이터에서 상태별 기한 인덱스를 사용하고 커서를 순환하는지 확인한다. */
@Testcontainers
class EventLifecycleIndexIntegrationTest {
	@Container static final MySQLContainer MYSQL = MySqlTestContainers.create();
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 0, 0);

	@BeforeAll
	static void prepareSchemaAndEvents() throws Exception {
		Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
				.locations("classpath:db/migration").load().migrate();
		try (Connection connection = connect(); PreparedStatement insert = connection.prepareStatement("""
				insert into events(id,title,description,event_type,weighting_enabled,membership_rule,
				starts_at,ends_at,status,created_at,updated_at)
				values(?,'인덱스 확인','설명','NO_TICKET',false,'vip',?,?,?,?,?)
				""")) {
			connection.setAutoCommit(false);
			for (int index = 0; index < 2004; index++) {
				boolean scheduled = index % 2 == 0;
				boolean due = index >= 2000;
				insert.setBytes(1, UuidBinary.toBytes(new UUID(1, index)));
				insert.setObject(2, scheduled && !due ? NOW.plusDays(1) : NOW.minusMinutes(2));
				insert.setObject(3, !scheduled && due ? NOW.minusMinutes(1) : NOW.plusDays(2));
				insert.setString(4, scheduled ? "SCHEDULED" : "OPEN");
				insert.setObject(5, NOW);
				insert.setObject(6, NOW);
				insert.addBatch();
			}
			insert.executeBatch();
			connection.commit();
			try (java.sql.Statement statistics = connection.createStatement()) { statistics.execute("analyze table events"); }
		}
	}

	@Test
	@DisplayName("미래 이벤트 2000개를 제외해 상태별 시간 인덱스를 범위 조회하고 due 후보 네 개만 순환 반환한다")
	void usesDeadlineIndexesAndRotatesOnlyDueCandidates() throws Exception {
		// given
		try (Connection connection = connect()) {
			for (boolean rotated : new boolean[] {false, true}) {
				byte[] cursor = rotated ? UuidBinary.toBytes(new UUID(1, 2001)) : null;
				// when / then: 운영 저장소와 같은 SQL의 실제 선택 인덱스를 EXPLAIN으로 확인한다.
				try (PreparedStatement explain = connection.prepareStatement("explain " + EventRepositoryImpl.LIFECYCLE_DUE_SQL)) {
					bind(explain, cursor);
					List<String> indexes = new ArrayList<>();
					try (ResultSet rows = explain.executeQuery()) {
						while (rows.next()) {
							if ("events".equals(rows.getString("table"))) {
								indexes.add(rows.getString("key"));
								assertThat(rows.getString("type")).isEqualTo("range");
							}
						}
					}
					assertThat(indexes).containsExactlyInAnyOrder("idx_events_lifecycle_start", "idx_events_lifecycle_end");
				}
				try (PreparedStatement query = connection.prepareStatement(EventRepositoryImpl.LIFECYCLE_DUE_SQL)) {
					bind(query, cursor);
					List<UUID> ids = new ArrayList<>();
					try (ResultSet rows = query.executeQuery()) {
						while (rows.next()) { ids.add(UuidBinary.fromBytes(rows.getBytes("id"))); }
					}
					assertThat(ids).containsExactlyElementsOf(rotated
							? List.of(new UUID(1, 2002), new UUID(1, 2003), new UUID(1, 2000), new UUID(1, 2001))
							: List.of(new UUID(1, 2000), new UUID(1, 2001), new UUID(1, 2002), new UUID(1, 2003)));
				}
			}
		}
	}

	private void bind(PreparedStatement statement, byte[] cursor) throws Exception {
		statement.setObject(1, NOW);
		statement.setObject(2, NOW);
		statement.setBytes(3, cursor);
		statement.setBytes(4, cursor);
		statement.setInt(5, 20);
	}

	private static Connection connect() throws Exception {
		return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
	}
}
