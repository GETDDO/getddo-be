package com.getddo.db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

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
	@DisplayName("빈 MySQL에 V001~V011을 적용하고 논리 삭제 컬럼과 재실행을 확인한다")
	void migratesSchemaAndDoesNotReapplyIt() throws SQLException {
		// given
		Flyway flyway = Flyway.configure()
				.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
				.locations("classpath:db/migration")
				.load();

		// when
		int appliedMigrations = flyway.migrate().migrationsExecuted;
		int repeatedMigrations = flyway.migrate().migrationsExecuted;

		// then
		assertThat(appliedMigrations).isEqualTo(11);
		assertThat(repeatedMigrations).isZero();
		assertThat(flyway.info().pending()).isEmpty();
		flyway.validate();
		assertNullableDeletedAtColumn("events");
		assertNullableDeletedAtColumn("banners");
	}

	private void assertNullableDeletedAtColumn(String tableName) throws SQLException {
		try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
			ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, "deleted_at")) {
			assertThat(columns.next()).isTrue();
			assertThat(columns.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNullable);
		}
	}
}
