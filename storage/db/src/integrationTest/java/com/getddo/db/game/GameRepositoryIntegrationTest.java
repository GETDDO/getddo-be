package com.getddo.db.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.getddo.core.game.domain.Game;
import com.getddo.core.game.repository.GameRepository;
import com.getddo.db.ticket.TicketIntegrationTestApplication;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = {TicketIntegrationTestApplication.class, GamePersistenceTestConfiguration.class},
		properties = "spring.jpa.hibernate.ddl-auto=validate")
class GameRepositoryIntegrationTest {

	private static final String RULES = "{\"initialLives\":3,\"pointsPerCorrect\":10}";
	@Autowired private GameRepository games;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private TransactionTemplate transaction;
	private final List<UUID> createdIds = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		for (UUID id : createdIds) {
			jdbc.update("delete from games where id = ?", bytes(id));
		}
	}

	@Test
	@DisplayName("게임 규칙을 JSON으로 저장하고 UUID v7과 코드로 조회한다")
	void storesAndFindsGame() {
		// given
		String code = "test-" + UUID.randomUUID();
		// when
		Game saved = insert(code);
		Game byId = transaction.execute(status -> games.findById(saved.getId()).orElseThrow());
		Game byCode = transaction.execute(status -> games.findByCode(code).orElseThrow());
		// then
		assertThat(saved.getId().version()).isEqualTo(7);
		assertThat(byId).usingRecursiveComparison().ignoringFields("rules").isEqualTo(saved);
		assertThat(byCode).usingRecursiveComparison().ignoringFields("rules").isEqualTo(saved);
		assertThat(byId.getRules()).isEqualTo(byCode.getRules());
		assertThat(jdbc.queryForObject("select JSON_EXTRACT(rules, '$.initialLives') from games where id = ?",
				Integer.class, bytes(saved.getId()))).isEqualTo(3);
		assertThat(jdbc.queryForObject("select JSON_EXTRACT(rules, '$.pointsPerCorrect') from games where id = ?",
				Integer.class, bytes(saved.getId()))).isEqualTo(10);
		assertThat(games.findById(UUID.randomUUID())).isEmpty();
		assertThat(games.findByCode("missing-" + UUID.randomUUID())).isEmpty();
	}

	@Test
	@DisplayName("활성 게임만 코드 순서로 조회한다")
	void findsOnlyActiveGamesInCodeOrder() {
		// given
		String prefix = "test-" + UUID.randomUUID();
		Game second = insert(prefix + "-b");
		Game inactive = insert(prefix + "-c");
		Game first = insert(prefix + "-a");
		jdbc.update("update games set is_active = false where id = ?", bytes(inactive.getId()));
		// when
		List<Game> active = transaction.execute(status -> games.findActive());
		// then
		assertThat(active).allMatch(Game::isActive);
		assertThat(active).extracting(Game::getCode).isSorted();
		assertThat(active).extracting(Game::getId).contains(first.getId(), second.getId())
				.doesNotContain(inactive.getId());
		assertThat(games.findById(inactive.getId()).orElseThrow().isActive()).isFalse();
	}

	@Test
	@DisplayName("중복 코드와 기존 게임의 재등록을 거절하고 최초 규칙을 보존한다")
	void rejectsDuplicateAndExistingGame() {
		// given
		String code = "test-" + UUID.randomUUID();
		Game saved = insert(code);
		// when / then
		assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
				games.insert(Game.create(code, "중복", null, "{}", "v2"))))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> games.insert(saved)))
				.isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
				.hasRootCauseInstanceOf(IllegalArgumentException.class);
		assertThat(games.findByCode(code).orElseThrow().getRuleVersion()).isEqualTo("v1");
		assertThat(jdbc.queryForObject("select count(*) from games where code = ?", Long.class, code)).isEqualTo(1);
	}

	private Game insert(String code) {
		Game saved = transaction.execute(status -> games.insert(Game.create(code, "색깔", "설명", RULES, "v1")));
		createdIds.add(saved.getId());
		return saved;
	}
}
