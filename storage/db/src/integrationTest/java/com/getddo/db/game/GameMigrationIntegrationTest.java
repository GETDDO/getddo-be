package com.getddo.db.game;

import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.getddo.db.support.MySqlTestContainers;
import com.getddo.db.ticket.TicketGrantSeeds;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class GameMigrationIntegrationTest {

	@Container
	static final MySQLContainer MYSQL = MySqlTestContainers.create();

	@Test
	@DisplayName("V018까지 저장한 과거 플레이·보상 청구는 V020 적용 후 원본 값 그대로 남는다")
	void upgradesWithoutInventingVerificationData() {
		// given
		Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
				.locations("classpath:db/migration").target("18").load().migrate();
		JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
				MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
		TicketGrantSeeds seeds = new TicketGrantSeeds(jdbc);
		UUID user = seeds.user();
		TicketGrantSeeds.GameParents parents = seeds.gameParents(user);
		jdbc.update("""
				update game_plays set score = 0, result_hash = ?, completed_at = '2026-09-01 00:00:01'
				where id = ?
				""", "a".repeat(64), bytes(parents.playId()));
		UUID claimId = seeds.gameClaim(user, parents, 1);
		Map<String, Object> oldPlay = jdbc.queryForMap("select * from game_plays where id = ?", bytes(parents.playId()));
		Map<String, Object> oldClaim = jdbc.queryForMap("select * from game_reward_claims where id = ?", bytes(claimId));
		// when
		Flyway latest = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
				.locations("classpath:db/migration").load();
		int pending = latest.info().pending().length;
		int applied = latest.migrate().migrationsExecuted;
		// then
		assertThat(applied).isPositive().isEqualTo(pending);
		Map<String, Object> newPlay = jdbc.queryForMap("select * from game_plays where id = ?", bytes(parents.playId()));
		for (String column : new String[] {"rules_snapshot", "play_token_hash", "generator_version", "generation_seed"}) {
			assertThat(newPlay).containsEntry(column, null);
			newPlay.remove(column);
		}
		assertThat(newPlay).usingRecursiveComparison().isEqualTo(oldPlay);
		assertThat(jdbc.queryForMap("select * from game_reward_claims where id = ?", bytes(claimId)))
				.usingRecursiveComparison().isEqualTo(oldClaim);
		latest.validate();
		assertThat(latest.migrate().migrationsExecuted).isZero();
	}
}
