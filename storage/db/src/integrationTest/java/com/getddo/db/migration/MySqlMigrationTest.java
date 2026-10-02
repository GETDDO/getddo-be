package com.getddo.db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.getddo.db.support.MySqlTestContainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MySqlMigrationTest {

	@Container
	static final MySQLContainer MYSQL = MySqlTestContainers.create();

	@Test
	@DisplayName("V001~V011의 기존 데이터를 유지하며 V012 인덱스를 적용하고 재실행을 확인한다")
	void migratesSchemaAndDoesNotReapplyIt() throws SQLException {
		// given
		Flyway initialSchema = Flyway.configure()
				.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
				.locations("classpath:db/migration")
				.target("11")
				.load();
		assertThat(initialSchema.migrate().migrationsExecuted).isEqualTo(11);
		insertExistingEventAndPrize();
		Flyway flyway = Flyway.configure()
				.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
				.locations("classpath:db/migration")
				.load();

		// when
		int appliedMigrations = flyway.migrate().migrationsExecuted;
		int repeatedMigrations = flyway.migrate().migrationsExecuted;

		// then
		assertThat(appliedMigrations).isEqualTo(1);
		assertThat(repeatedMigrations).isZero();
		assertThat(flyway.info().pending()).isEmpty();
		flyway.validate();
		assertNullableDeletedAtColumn("events");
		assertNullableDeletedAtColumn("banners");
		assertEventListIndex();
		assertExistingEventAndPrize();
	}

	private Connection connection() throws SQLException {
		return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
	}

	private void insertExistingEventAndPrize() throws SQLException {
		try (Connection connection = connection(); Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
					insert into users(id,name,role,status,created_at,updated_at)
					values(UNHEX('00000000000000000000000000000001'),'관리자','ADMIN','ACTIVE',
					       '2026-10-01 00:00:00','2026-10-01 00:00:00')
					""");
			statement.executeUpdate("""
					insert into events(id,created_by,title,description,event_type,weighting_enabled,
					                   membership_rule,starts_at,ends_at,status,created_at,updated_at)
					values(UNHEX('00000000000000000000000000000002'),UNHEX('00000000000000000000000000000001'),
					       '기존 이벤트','기존 설명','NO_TICKET',false,'excellent',
					       '2026-10-10 00:00:00','2026-10-11 00:00:00','SCHEDULED',
					       '2026-10-01 00:00:00','2026-10-01 00:00:00')
					""");
			statement.executeUpdate("""
					insert into event_prizes(id,event_id,prize_rank,name,winner_count,created_at,updated_at)
					values(UNHEX('00000000000000000000000000000003'),UNHEX('00000000000000000000000000000002'),
					       1,'기존 경품',2,'2026-10-01 00:00:00','2026-10-01 00:00:00')
					""");
		}
	}

	private void assertEventListIndex() throws SQLException {
		List<String> columns = new ArrayList<>();
		try (Connection connection = connection();
				ResultSet indexes = connection.getMetaData().getIndexInfo(null, null, "events", false, false)) {
			while (indexes.next()) {
				if ("idx_events_list".equals(indexes.getString("INDEX_NAME"))) {
					assertThat(indexes.getBoolean("NON_UNIQUE")).isTrue();
					columns.add(indexes.getString("COLUMN_NAME"));
				}
			}
		}
		assertThat(columns).containsExactly("deleted_at", "created_at", "id");
	}

	private void assertExistingEventAndPrize() throws SQLException {
		try (Connection connection = connection(); Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery("""
						select e.title,e.description,p.name,p.winner_count from events e
						join event_prizes p on p.event_id=e.id
						where e.id=UNHEX('00000000000000000000000000000002') and e.deleted_at is null
						""")) {
			assertThat(rows.next()).isTrue();
			assertThat(rows.getString("title")).isEqualTo("기존 이벤트");
			assertThat(rows.getString("description")).isEqualTo("기존 설명");
			assertThat(rows.getString("name")).isEqualTo("기존 경품");
			assertThat(rows.getInt("winner_count")).isEqualTo(2);
			assertThat(rows.next()).isFalse();
		}
	}

	private void assertNullableDeletedAtColumn(String tableName) throws SQLException {
		try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
			ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, "deleted_at")) {
			assertThat(columns.next()).isTrue();
			assertThat(columns.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNullable);
		}
	}
}
