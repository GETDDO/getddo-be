package com.getddo.db.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MySqlMigrationTest {

	@Container
	static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

	@Test
	@DisplayName("빈 MySQL에 초기 SQL을 적용하고 재실행해도 중복 적용하지 않는다")
	void migratesInitialSchemaAndDoesNotReapplyIt() {
		// given
		Flyway flyway = Flyway.configure()
				.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
				.locations("classpath:db/migration")
				.load();

		// when
		int initialMigrations = flyway.migrate().migrationsExecuted;
		int repeatedMigrations = flyway.migrate().migrationsExecuted;

		// then
		assertThat(initialMigrations).isEqualTo(11);
		assertThat(repeatedMigrations).isZero();
		assertThat(flyway.info().pending()).isEmpty();
		flyway.validate();
	}
}
