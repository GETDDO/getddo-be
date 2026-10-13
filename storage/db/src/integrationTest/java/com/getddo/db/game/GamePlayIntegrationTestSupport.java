package com.getddo.db.game;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.game.domain.Game;
import com.getddo.core.game.domain.GamePlay;
import com.getddo.core.game.repository.GamePlayRepository;
import com.getddo.core.game.repository.GameRepository;
import com.getddo.db.ticket.LockWaitProbe;
import com.getddo.db.ticket.MutableClock;
import com.getddo.db.ticket.TicketGrantSeeds;
import com.getddo.db.ticket.TicketIntegrationTestApplication;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;

@SpringBootTest(classes = {TicketIntegrationTestApplication.class, GamePersistenceTestConfiguration.class},
		properties = "spring.jpa.hibernate.ddl-auto=validate")
abstract class GamePlayIntegrationTestSupport {

	protected static final String PLAY_TOKEN_HASH = "a".repeat(64);
	protected static final String RESULT_HASH = "b".repeat(64);
	protected static final String RULES = "{\"initialLives\":3,\"pointsPerCorrect\":10}";
	@Autowired protected GameRepository games;
	@Autowired protected GamePlayRepository plays;
	@Autowired protected TimeProvider time;
	@Autowired protected MutableClock clock;
	@Autowired protected JdbcTemplate jdbc;
	@Autowired protected TransactionTemplate transaction;
	@Autowired private MySQLContainer mysql;

	protected TicketGrantSeeds seeds;
	protected TicketGrantSeeds.GameParents parents;
	protected UUID userId;
	protected Game game;
	protected LockWaitProbe lockWaits;

	@BeforeEach
	void setUp() {
		clock.set(TicketIntegrationTestApplication.INITIAL_TIME);
		seeds = new TicketGrantSeeds(jdbc);
		lockWaits = new LockWaitProbe(mysql);
		userId = seeds.user();
		parents = seeds.gameParents(userId);
		jdbc.update("update games set rules = ?, rule_version = 'color-word-v1' where id = ?",
				RULES, bytes(parents.gameId()));
		game = transaction.execute(status -> games.findById(parents.gameId()).orElseThrow());
	}

	@AfterEach
	void cleanUp() {
		seeds.cleanUp();
	}

	protected GamePlay start() {
		return transaction.execute(status -> plays.insert(
				GamePlay.start(game, userId, PLAY_TOKEN_HASH, "color-word-generator-v1", 4294967295L)));
	}

	protected GamePlay complete(GamePlay play, Long score) {
		return transaction.execute(status -> plays.complete(play.complete(score, RESULT_HASH, time.now())));
	}

}
