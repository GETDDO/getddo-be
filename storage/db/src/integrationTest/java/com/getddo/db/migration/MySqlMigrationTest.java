package com.getddo.db.migration;

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
	@DisplayName("빈 MySQL에 Flyway SQL을 적용하고 재실행해도 중복 적용하지 않는다")
	void migratesSchemaAndDoesNotReapplyIt() {
		// given
		Flyway flyway = Flyway.configure()
				.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
				.locations("classpath:db/migration")
				.load();

		// when
		int appliedMigrations = flyway.migrate().migrationsExecuted;
		int repeatedMigrations = flyway.migrate().migrationsExecuted;

		// then
		assertThat(appliedMigrations).isEqualTo(12);
		assertThat(repeatedMigrations).isZero();
		assertThat(flyway.info().pending()).isEmpty();
		flyway.validate();
	}
}
