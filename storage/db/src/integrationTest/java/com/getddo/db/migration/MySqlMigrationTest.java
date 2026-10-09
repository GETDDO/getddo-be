package com.getddo.db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.getddo.db.support.MySqlTestContainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class MySqlMigrationTest {

	@Container
	static final MySQLContainer MYSQL = MySqlTestContainers.create();

	private static Flyway flyway;
	private static int appliedMigrations;

	/** 스키마 검증 사례들이 공유할 빈 테스트 DB에 전체 마이그레이션을 한 번 적용한다. */
	@BeforeAll
	static void migrate() {
		flyway = Flyway.configure()
				.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
				.locations("classpath:db/migration")
				.load();
		appliedMigrations = flyway.migrate().migrationsExecuted;
	}

	/** 빈 DB에 이벤트·알림 인덱스를 포함한 스키마를 적용하고 재실행의 멱등성을 검증한다. */
	@Test
	@DisplayName("빈 MySQL에 V001~V011·V014·V015의 44개 테이블·이벤트·알림 인덱스와 재실행을 확인한다")
	void migratesSchemaAndDoesNotReapplyIt() throws SQLException {
		// given
		// when
		int repeatedMigrations = flyway.migrate().migrationsExecuted;

		// then
		assertThat(appliedMigrations).isEqualTo(13);
		assertThat(repeatedMigrations).isZero();
		assertThat(flyway.info().pending()).isEmpty();
		flyway.validate();
		assertNullableDeletedAtColumn("events");
		assertThat(indexColumns("events", "idx_events_list", false))
				.containsExactly("deleted_at", "created_at", "id");
		assertIndexExists("notification_jobs", "ix_notification_job_pending");
		assertIndexExists("notification_jobs", "ix_notification_job_lease");
		assertIndexExists("notifications", "ix_notification_delivery_retry");
		try (Connection connection = connect();
			ResultSet rows = connection.createStatement().executeQuery("""
					select count(*) from information_schema.tables
					where table_schema = database() and table_name <> 'flyway_schema_history'
					""")) {
			assertThat(rows.next()).isTrue();
			assertThat(rows.getInt(1)).isEqualTo(44);
		}
		try (Connection connection = connect();
			ResultSet rows = connection.createStatement().executeQuery("""
					select count(*) from information_schema.columns
					where table_schema = database() and table_name = 'notification_jobs'
					and column_name in ('publication_id', 'target_user_id')
					""")) {
			assertThat(rows.next()).isTrue();
			assertThat(rows.getInt(1)).isZero();
		}
	}

	@Test
	@DisplayName("개별 응모권·이력과 게임 통계의 PK 및 중복 방지 UNIQUE를 확인한다")
	void ticketsHistoriesAndGameStatsUseSingleIdPrimaryKey() throws SQLException {
		// given
		// when
		// then
		assertThat(idColumn("tickets")).containsExactly("binary(16)", "NO");
		assertThat(idColumn("ticket_histories")).containsExactly("binary(16)", "NO");
		assertThat(idColumn("user_game_stats")).containsExactly("binary(16)", "NO");
		assertThat(indexColumns("tickets", "PRIMARY")).containsExactly("id");
		assertThat(indexColumns("ticket_histories", "PRIMARY")).containsExactly("id");
		assertThat(indexColumns("ticket_histories", "uq_ticket_histories_1"))
				.containsExactly("ticket_id", "ticket_version");
		assertThat(indexColumns("ticket_histories", "uq_ticket_histories_refund_source"))
				.containsExactly("original_use_history_id");
		assertThat(indexColumns("draw_run_candidates", "PRIMARY")).containsExactly("draw_run_id", "candidate_id");
		assertThat(indexColumns("draw_publication_results", "PRIMARY"))
				.containsExactly("publication_id", "draw_result_id");
		assertThat(indexColumns("user_game_stats", "PRIMARY")).containsExactly("id");
		assertThat(indexColumns("user_game_stats", "uq_user_game_stats_1")).containsExactly("user_id", "game_id");
	}

	@Test
	@DisplayName("출석 응모권은 브론즈 및 지급 근거 소유자를 검증하고 이력 버전 중복을 거부한다")
	void enforcesTicketSourceGradeAndHistoryVersion() throws SQLException {
		try (Connection connection = connect()) {
			execute(connection, """
					insert into users (id, name, role, status, created_at, updated_at) values
					(unhex('0199A0000000700080000000000000B1'), '티켓사용자', 'USER', 'ACTIVE', now(6), now(6)),
					(unhex('0199A0000000700080000000000000B2'), '다른사용자', 'USER', 'ACTIVE', now(6), now(6))
					""");
			execute(connection, """
					insert into reward_policies (id, created_by, reward_type, reward_ticket_count, effective_from, created_at)
					values (unhex('0199A0000000700080000000000000B3'), unhex('0199A0000000700080000000000000B1'),
					        'ATTENDANCE', 1, now(6), now(6))
					""");
			execute(connection, """
					insert into attendances (id, user_id, attendance_date, created_at)
					values (unhex('0199A0000000700080000000000000B4'), unhex('0199A0000000700080000000000000B1'),
					        '2026-10-08', now(6))
					""");
			execute(connection, """
					insert into attendance_reward_claims
					(id, attendance_id, reward_policy_id, user_id, reward_type, reward_date, source_key, ticket_count, created_at)
					values (unhex('0199A0000000700080000000000000B5'), unhex('0199A0000000700080000000000000B4'),
					        unhex('0199A0000000700080000000000000B3'), unhex('0199A0000000700080000000000000B1'),
					        'DAILY', '2026-10-08', '2026-10-08', 1, now(6))
					""");
			String insertTicket = """
					insert into tickets (id, user_id, attendance_reward_claim_id, grade, status, expires_at, created_at, updated_at)
					values (unhex(?), unhex(?), unhex('0199A0000000700080000000000000B5'), ?, 'AVAILABLE',
					        '2026-11-01 00:00:00', now(6), now(6))
					""";
			assertThatThrownBy(() -> execute(connection, insertTicket, "0199A0000000700080000000000000B6",
					"0199A0000000700080000000000000B1", "GOLD"))
					.isInstanceOf(SQLException.class).hasMessageContaining("chk_ticket_attendance_bronze");
			assertThatThrownBy(() -> execute(connection, insertTicket, "0199A0000000700080000000000000B6",
					"0199A0000000700080000000000000B2", "BRONZE"))
					.isInstanceOf(SQLIntegrityConstraintViolationException.class);
			execute(connection, insertTicket, "0199A0000000700080000000000000B6",
					"0199A0000000700080000000000000B1", "BRONZE");
			String insertHistory = """
					insert into ticket_histories (id, ticket_id, operation_type, ticket_version, status, expires_at, reason, created_at)
					values (unhex(?), unhex('0199A0000000700080000000000000B6'), 'GRANT', 1, 'AVAILABLE',
					        '2026-11-01 00:00:00', '출석 지급', now(6))
					""";
			execute(connection, insertHistory, "0199A0000000700080000000000000B7");
			assertThatThrownBy(() -> execute(connection, insertHistory, "0199A0000000700080000000000000B8"))
					.isInstanceOf(SQLIntegrityConstraintViolationException.class)
					.hasMessageContaining("uq_ticket_histories_1");
		}
	}

	@Test
	@DisplayName("같은 사용자·게임의 게임 통계 두 번째 행은 UNIQUE로 거부한다")
	void rejectsDuplicateUserGameStats() throws SQLException {
		try (Connection connection = connect()) {
			// given
			execute(connection, """
					insert into users (id, name, role, status, membership, updated_at, created_at)
					values (unhex('0199A0000000700080000000000000A1'), '통계사용자', 'USER', 'ACTIVE', 'VIP', now(6), now(6))
					""");
			execute(connection, """
					insert into games (id, code, name, rules, rule_version, created_at, updated_at)
					values (unhex('0199A0000000700080000000000000A2'), 'stats-unique', '통계 게임', '{}', 'v1', now(6), now(6))
					""");
			String insertStats = """
					insert into user_game_stats (id, user_id, game_id, updated_at, created_at)
					values (unhex(?), unhex('0199A0000000700080000000000000A1'), unhex('0199A0000000700080000000000000A2'),
					        now(6), now(6))
					""";
			execute(connection, insertStats, "0199A0000000700080000000000000A3");
			// when
			// then
			assertThatThrownBy(() -> execute(connection, insertStats, "0199A0000000700080000000000000A4"))
					.isInstanceOf(SQLIntegrityConstraintViolationException.class)
					.hasMessageContaining("uq_user_game_stats_1");
		}
	}

	private static Connection connect() throws SQLException {
		return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
	}

	private static void execute(Connection connection, String sql, String... parameters) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < parameters.length; i++) {
				statement.setString(i + 1, parameters[i]);
			}
			statement.executeUpdate();
		}
	}

	/** {@code id} 컬럼의 타입과 NULL 허용 여부({@code YES}/{@code NO})를 반환한다. */
	private static List<String> idColumn(String tableName) throws SQLException {
		try (Connection connection = connect();
			PreparedStatement statement = connection.prepareStatement("""
					select column_type, is_nullable from information_schema.columns
					where table_schema = database() and table_name = ? and column_name = 'id'
					""")) {
			statement.setString(1, tableName);
			try (ResultSet rows = statement.executeQuery()) {
				assertThat(rows.next()).as("%s.id 컬럼", tableName).isTrue();
				return List.of(rows.getString(1), rows.getString(2));
			}
		}
	}

	/** 유니크 인덱스(PK 포함)의 컬럼을 인덱스 내 순서대로 반환한다. */
	private static List<String> indexColumns(String tableName, String indexName) throws SQLException {
		return indexColumns(tableName, indexName, true);
	}

	/** 일반 인덱스도 검사할 수 있도록 유니크 여부 제한을 선택한다. */
	private static List<String> indexColumns(String tableName, String indexName, boolean uniqueOnly) throws SQLException {
		try (Connection connection = connect();
			PreparedStatement statement = connection.prepareStatement("""
					select column_name from information_schema.statistics
					where table_schema = database() and table_name = ? and index_name = ?
					and (? = false or non_unique = 0)
					order by seq_in_index
					""")) {
			statement.setString(1, tableName);
			statement.setString(2, indexName);
			statement.setBoolean(3, uniqueOnly);
			List<String> columns = new ArrayList<>();
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					columns.add(rows.getString(1));
				}
			}
			return columns;
		}
	}

	/** MySQL 메타데이터에서 워커 선점·재시도에 필요한 인덱스의 적용을 확인한다. */
	private void assertIndexExists(String tableName, String indexName) throws SQLException {
		try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
			ResultSet indexes = connection.getMetaData().getIndexInfo(null, null, tableName, false, false)) {
			boolean found = false;
			while (indexes.next()) {
				found |= indexName.equals(indexes.getString("INDEX_NAME"));
			}
			assertThat(found).as(indexName).isTrue();
		}
	}

	/** 논리 삭제 컬럼이 기존 활성 데이터를 표현할 수 있도록 null을 허용하는지 확인한다. */
	private void assertNullableDeletedAtColumn(String tableName) throws SQLException {
		try (Connection connection = connect();
			ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, "deleted_at")) {
			assertThat(columns.next()).isTrue();
			assertThat(columns.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNullable);
		}
	}
}
