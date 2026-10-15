package com.getddo.api.game;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.getddo.api.support.ApiIntegrationTest;
import com.getddo.core.game.domain.GamePlay;
import com.getddo.core.game.domain.GamePlayCredentials;
import com.getddo.core.game.repository.GamePlayRepository;
import com.getddo.db.common.util.UuidBinary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** G03 시작 정보와 실제 저장값, 시작만으로 통계·보상이 생기지 않는지 검증한다. */
@ApiIntegrationTest
@Import(GamePlayApiIntegrationTest.FixedTimeConfiguration.class)
class GamePlayApiIntegrationTest {

	private static final UUID USER = UUID.randomUUID();
	private static final UUID ADMIN = UUID.randomUUID();
	private static final UUID GAME = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-10-14T01:00:00.123456789Z");
	private static final Instant STORED_TIME = Instant.parse("2026-10-14T01:00:00.123456Z");
	private static final String RULES = """
			{"initialLives":3,"pointsPerCorrect":10,"initialTimeLimitMs":3000,
			 "timeLimitDecreaseMs":80,"minimumTimeLimitMs":1200,"internalNote":"private"}
			""";
	private final JsonMapper json = JsonMapper.builder().build();
	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private GamePlayRepository plays;

	@BeforeEach
	void seed() {
		LocalDateTime now = LocalDateTime.ofInstant(STORED_TIME, ZoneOffset.UTC);
		jdbc.update("""
				insert into users (id, name, role, status, membership, created_at, updated_at)
				values (?, '게임 사용자', 'USER', 'ACTIVE', 'vip', ?, ?),
				       (?, '게임 관리자', 'ADMIN', 'ACTIVE', null, ?, ?)
				""", UuidBinary.toBytes(USER), now, now, UuidBinary.toBytes(ADMIN), now, now);
		jdbc.update("""
				insert into games (id, code, name, rules, rule_version, is_active, created_at, updated_at)
				values (?, 'COLOR_WORD', '글자색깔 맞추기', ?, 'color-word-v1', true, ?, ?)
				""", UuidBinary.toBytes(GAME), RULES, now, now);
	}

	@AfterEach
	void clean() {
		jdbc.update("delete from game_plays where game_id = ?", UuidBinary.toBytes(GAME));
		jdbc.update("delete from games where id = ?", UuidBinary.toBytes(GAME));
		jdbc.update("delete from users where id in (?, ?)", UuidBinary.toBytes(USER), UuidBinary.toBytes(ADMIN));
	}

	@Test
	@DisplayName("시작 응답은 실제 저장 ID·마이크로초 시각·seed·규칙과 일치하고 토큰 원문은 저장하지 않는다")
	void responseMatchesPersistedContext() throws Exception {
		// given / when
		JsonNode data = start(USER, "USER");
		// then
		UUID playId = UUID.fromString(data.get("playId").stringValue());
		GamePlay stored = plays.find(playId, USER, GAME).orElseThrow();
		assertThat(playId.version()).isEqualTo(7);
		assertThat(stored.getStartedAt()).isEqualTo(STORED_TIME);
		assertThat(data.get("startedAt").stringValue()).isEqualTo(STORED_TIME.toString());
		assertThat(stored.getRuleVersion()).isEqualTo(data.get("ruleVersion").stringValue());
		assertThat(stored.getGeneratorVersion()).isEqualTo(data.get("gameData").get("generatorVersion").stringValue());
		assertThat(stored.getGenerationSeed()).isEqualTo(data.get("gameData").get("seed").longValue());
		assertThat(stored.getGenerationSeed()).isBetween(1L, 4294967295L);
		assertThat(json.readTree(stored.getRulesSnapshot())).isEqualTo(json.readTree(RULES));
		String token = data.get("playToken").stringValue();
		String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(token.getBytes(StandardCharsets.UTF_8)));
		assertThat(stored.getPlayTokenHash()).isEqualTo(expectedHash).isNotEqualTo(token);
		assertThat(GamePlayCredentials.matches(stored, USER, GAME, token)).isTrue();
		assertThat(GamePlayCredentials.matches(stored, ADMIN, GAME, token)).isFalse();
		assertThat(GamePlayCredentials.matches(stored, USER, UUID.randomUUID(), token)).isFalse();
		assertThat(token).matches("[A-Za-z0-9_-]{43}");
		assertThat(data.size()).isEqualTo(6);
		assertThat(data.get("rules").size()).isEqualTo(5);
		assertThat(data.get("rules").has("internalNote")).isFalse();
		assertThat(stored.isCompleted()).isFalse();
		assertThat(stored.getScore()).isNull();
		assertThat(stored.getResultHash()).isNull();
		assertThat(count("user_game_stats", USER)).isZero();
		assertThat(count("game_reward_claims", USER)).isZero();
		assertThat(count("tickets", USER)).isZero();
	}

	@Test
	@DisplayName("본문으로 다른 사용자·규칙·seed·점수를 보내도 시작 기준을 덮어쓸 수 없다")
	void doesNotUseClientSuppliedStartValues() throws Exception {
		// given
		String ignoredBody = """
				{"userId":"%s","ruleVersion":"client-rule","seed":0,"score":999,
				 "rules":{"initialLives":99}}
				""".formatted(ADMIN);
		// when
		MvcResult response = mvc.perform(request(USER, "USER", GAME)
						.contentType(MediaType.APPLICATION_JSON).content(ignoredBody))
				.andExpect(status().isCreated()).andReturn();
		JsonNode data = json.readTree(response.getResponse().getContentAsString()).get("data");
		// then
		UUID playId = UUID.fromString(data.get("playId").stringValue());
		GamePlay stored = plays.find(playId, USER, GAME).orElseThrow();
		assertThat(plays.find(playId, ADMIN, GAME)).isEmpty();
		assertThat(stored.getRuleVersion()).isEqualTo("color-word-v1");
		assertThat(stored.getGenerationSeed()).isBetween(1L, 4294967295L);
		assertThat(data.get("rules").get("initialLives").intValue()).isEqualTo(3);
		assertThat(stored.getScore()).isNull();
		assertThat(stored.getCompletedAt()).isNull();
	}

	@Test
	@DisplayName("활성 관리자도 시작할 수 있고 관리자 본인의 플레이로 저장한다")
	void activeAdminCanStart() throws Exception {
		// given / when
		JsonNode data = start(ADMIN, "ADMIN");
		// then
		UUID playId = UUID.fromString(data.get("playId").stringValue());
		assertThat(plays.find(playId, ADMIN, GAME)).isPresent();
		assertThat(plays.find(playId, USER, GAME)).isEmpty();
		assertThat(count("game_plays", ADMIN)).isEqualTo(1);
		assertThat(count("user_game_stats", ADMIN)).isZero();
		assertThat(count("game_reward_claims", ADMIN)).isZero();
		assertThat(count("tickets", ADMIN)).isZero();
	}

	@Test
	@DisplayName("다시 시작하면 별도 플레이를 발급하며 시작 후 설정 변경은 기존 스냅샷을 바꾸지 않는다")
	void issuesDistinctPlaysAndPreservesSnapshots() throws Exception {
		// given / when
		JsonNode first = start(USER, "USER");
		JsonNode second = start(USER, "USER");
		jdbc.update("update games set rules = ?, rule_version = 'color-word-v2', is_active = false where id = ?",
				"{\"initialLives\":9}", UuidBinary.toBytes(GAME));
		// then
		assertThat(first.get("playId")).isNotEqualTo(second.get("playId"));
		assertThat(first.get("playToken")).isNotEqualTo(second.get("playToken"));
		assertThat(count("game_plays", USER)).isEqualTo(2);
		GamePlay secondPlay = plays.find(UUID.fromString(second.get("playId").stringValue()), USER, GAME).orElseThrow();
		assertThat(GamePlayCredentials.matches(secondPlay, USER, GAME, first.get("playToken").stringValue())).isFalse();
		GamePlay original = plays.find(UUID.fromString(first.get("playId").stringValue()), USER, GAME).orElseThrow();
		assertThat(original.getRuleVersion()).isEqualTo("color-word-v1");
		assertThat(json.readTree(original.getRulesSnapshot())).isEqualTo(json.readTree(RULES));
		mvc.perform(request(USER, "USER", GAME)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GAME-003"));
		assertThat(count("game_plays", USER)).isEqualTo(2);
	}

	@Test
	@DisplayName("비활성 사용자·관리자는 403이며 사용자 역할 헤더를 위조하면 공통 403이다")
	void rejectsInactiveAccountsAndRoleMismatch() throws Exception {
		// given
		jdbc.update("update users set status = 'INACTIVE' where id in (?, ?)",
				UuidBinary.toBytes(USER), UuidBinary.toBytes(ADMIN));
		// when / then
		mvc.perform(request(USER, "USER", GAME)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GAME-001"));
		mvc.perform(request(ADMIN, "ADMIN", GAME)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GAME-001"));
		mvc.perform(request(USER, "ADMIN", GAME)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("USER-004"));
		assertThat(count("game_plays", USER)).isZero();
		assertThat(count("game_plays", ADMIN)).isZero();
	}

	@Test
	@DisplayName("게임 부재·비활성·미지원 설정·잘못된 저장 규칙은 플레이를 만들지 않는다")
	void rejectsUnavailableOrInvalidConfiguration() throws Exception {
		// given / when / then
		mvc.perform(request(USER, "USER", UUID.randomUUID())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("GAME-002"));
		jdbc.update("update games set is_active = false where id = ?", UuidBinary.toBytes(GAME));
		mvc.perform(request(USER, "USER", GAME)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GAME-003"));
		jdbc.update("update games set is_active = true, rule_version = 'color-word-v2' where id = ?", UuidBinary.toBytes(GAME));
		mvc.perform(request(USER, "USER", GAME)).andExpect(status().isConflict());
		jdbc.update("update games set rule_version = 'color-word-v1', rules = '{}' where id = ?", UuidBinary.toBytes(GAME));
		mvc.perform(request(USER, "USER", GAME)).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("COMMON-001"));
		assertThat(count("game_plays", USER)).isZero();
	}

	private JsonNode start(UUID user, String role) throws Exception {
		MvcResult response = mvc.perform(request(user, role, GAME))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true)).andReturn();
		return json.readTree(response.getResponse().getContentAsString()).get("data");
	}

	private static MockHttpServletRequestBuilder request(UUID user, String role, UUID game) {
		return post("/api/v1/games/{gameId}/plays", game).header("X-User-ID", user).header("X-User-Role", role);
	}

	private int count(String table, UUID user) {
		return jdbc.queryForObject("select count(*) from " + table + " where user_id = ?",
				Integer.class, UuidBinary.toBytes(user));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTimeConfiguration {
		@Bean
		@Primary
		Clock gameStartClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}
}
