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

	@BeforeAll
	static void migrate() {
		flyway = Flyway.configure()
				.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
				.locations("classpath:db/migration")
				.load();
		appliedMigrations = flyway.migrate().migrationsExecuted;
	}

	@Test
	@DisplayName("빈 MySQL에 V001~V011·V014를 적용하고 논리 삭제 컬럼·알림 인덱스·재실행을 확인한다")
	void migratesSchemaAndDoesNotReapplyIt() throws SQLException {
		// given
		// when
		int repeatedMigrations = flyway.migrate().migrationsExecuted;

		// then
		assertThat(appliedMigrations).isEqualTo(12);
		assertThat(repeatedMigrations).isZero();
		assertThat(flyway.info().pending()).isEmpty();
		flyway.validate();
		assertNullableDeletedAtColumn("events");
		assertNullableDeletedAtColumn("banners");
		assertIndexExists("notification_jobs", "ix_notification_job_pending");
		assertIndexExists("notification_jobs", "ix_notification_job_lease");
		assertIndexExists("notifications", "ix_notification_delivery_retry");
	}

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

	@Test
	@DisplayName("응모권 배분과 게임 통계는 BINARY(16) 단일 id PK이고 기존 복합 키 조합은 UNIQUE로 남는다")
	void allocationAndGameStatsUseSingleIdPrimaryKey() throws SQLException {
		// given
		// when
		// then
		assertThat(idColumn("ticket_ledger_allocations")).containsExactly("binary(16)", "NO");
		assertThat(idColumn("user_game_stats")).containsExactly("binary(16)", "NO");
		assertThat(indexColumns("ticket_ledger_allocations", "PRIMARY")).containsExactly("id");
		assertThat(indexColumns("ticket_ledger_allocations", "uq_ticket_ledger_allocations_1"))
				.containsExactly("ledger_id", "source_credit_ledger_id", "original_grant_id");
		assertThat(indexColumns("user_game_stats", "PRIMARY")).containsExactly("id");
		assertThat(indexColumns("user_game_stats", "uq_user_game_stats_1")).containsExactly("user_id", "game_id");
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

	/** 인덱스(PK 포함)의 컬럼을 인덱스 내 순서대로 반환한다. */
	private static List<String> indexColumns(String tableName, String indexName) throws SQLException {
		try (Connection connection = connect();
			PreparedStatement statement = connection.prepareStatement("""
					select column_name from information_schema.statistics
					where table_schema = database() and table_name = ? and index_name = ? and non_unique = 0
					order by seq_in_index
					""")) {
			statement.setString(1, tableName);
			statement.setString(2, indexName);
			List<String> columns = new ArrayList<>();
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					columns.add(rows.getString(1));
				}
			}
			return columns;
		}
	}

	private void assertNullableDeletedAtColumn(String tableName) throws SQLException {
		try (Connection connection = connect();
			ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, "deleted_at")) {
			assertThat(columns.next()).isTrue();
			assertThat(columns.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNullable);
		}
	}
}
