package com.getddo.api.game;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.getddo.api.support.ApiIntegrationTest;
import com.getddo.db.common.util.UuidBinary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 저장 데이터·정렬·공개 규칙과 목록→상세→시작 연결을 검증한다. */
@ApiIntegrationTest
@Import(GameQueryApiIntegrationTest.FixedTimeConfiguration.class)
class GameQueryApiIntegrationTest {

	private static final UUID USER = UUID.randomUUID();
	private static final UUID ADMIN = UUID.randomUUID();
	private static final UUID COLOR = UUID.randomUUID();
	private static final UUID TAKKO = UUID.randomUUID();
	private static final UUID HIDDEN = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-10-15T04:30:00.123456Z");
	private static final String RULES = """
			{"initialLives":3,"pointsPerCorrect":10,"initialTimeLimitMs":3000,
			 "timeLimitDecreaseMs":80,"minimumTimeLimitMs":1200,"internalNote":"private-json"}
			""";
	private final JsonMapper json = JsonMapper.builder().build();
	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;

	@BeforeEach
	void seed() {
		LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
		jdbc.update("""
				insert into users (id, name, role, status, membership, created_at, updated_at)
				values (?, '조회 사용자', 'USER', 'ACTIVE', null, ?, ?),
				       (?, '조회 관리자', 'ADMIN', 'ACTIVE', null, ?, ?)
				""", UuidBinary.toBytes(USER), now, now, UuidBinary.toBytes(ADMIN), now, now);
		// 역순으로 저장해 실제 code 정렬을 확인한다.
		insert(TAKKO, "TAKKORUN", "v1", "{}", true, "타꼬런 안내");
		insert(HIDDEN, "HIDDEN_GD155", "v1", "{}", false, null);
		insert(COLOR, "COLOR_WORD", "color-word-v1", RULES, true, null);
	}

	@AfterEach
	void clean() {
		for (UUID id : List.of(COLOR, TAKKO, HIDDEN)) {
			jdbc.update("delete from game_plays where game_id = ?", UuidBinary.toBytes(id));
			jdbc.update("delete from games where id = ?", UuidBinary.toBytes(id));
		}
		jdbc.update("delete from users where id in (?, ?)", UuidBinary.toBytes(USER), UuidBinary.toBytes(ADMIN));
	}

	@Test
	@DisplayName("USER/ADMIN은 활성 게임 전체를 code순으로 조회하고 DB UUID로 상세·시작을 연결한다")
	void connectsListDetailAndStart() throws Exception {
		// given / when / then
		for (UUID user : List.of(USER, ADMIN)) {
			String role = user.equals(USER) ? "USER" : "ADMIN";
			mvc.perform(headers(get("/api/v1/games"), user, role)).andExpect(status().isOk())
					.andExpect(jsonPath("$.data", hasSize(2)))
					.andExpect(jsonPath("$.data[0].id").value(COLOR.toString()))
					.andExpect(jsonPath("$.data[0].code").value("COLOR_WORD"))
					.andExpect(jsonPath("$.data[1].id").value(TAKKO.toString()));
			mvc.perform(headers(get("/api/v1/games/{id}", COLOR), user, role)).andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id").value(COLOR.toString()))
					.andExpect(jsonPath("$.data.ruleVersion").value("color-word-v1"))
					.andExpect(jsonPath("$.data.serverTime").value(NOW.toString()))
					.andExpect(jsonPath("$.data.rules", aMapWithSize(5)))
					.andExpect(jsonPath("$.data.rules.internalNote").doesNotExist());
		}
		assertThat(playCount()).isZero();
		mvc.perform(headers(post("/api/v1/games/{id}/plays", COLOR), USER, "USER"))
				.andExpect(status().isCreated());
		assertThat(playCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("빈 목록·없는 UUID·잘못된 UUID·비활성 숨김과 미지원 상세 오류를 구분한다")
	void handlesVisibilityAndErrors() throws Exception {
		// given / when / then
		for (UUID id : List.of(HIDDEN, UUID.randomUUID())) {
			mvc.perform(headers(get("/api/v1/games/{id}", id), USER, "USER"))
					.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GAME-002"));
		}
		mvc.perform(headers(get("/api/v1/games/{id}", "bad-uuid"), USER, "USER"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-005"));
		mvc.perform(headers(get("/api/v1/games/{id}", TAKKO), USER, "USER"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GAME-004"));
		jdbc.update("update games set rule_version = 'color-word-v2' where id = ?", UuidBinary.toBytes(COLOR));
		mvc.perform(headers(get("/api/v1/games/{id}", COLOR), USER, "USER"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GAME-004"));
		jdbc.update("update games set is_active = false where id in (?, ?)",
				UuidBinary.toBytes(COLOR), UuidBinary.toBytes(TAKKO));
		mvc.perform(headers(get("/api/v1/games"), USER, "USER")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(0)));
		assertThat(playCount()).isZero();
	}

	@Test
	@DisplayName("저장된 공개 메타데이터는 nullable을 보존하고 설정 오류는 공통 500으로 숨긴다")
	void readsDatabaseChangesAndHidesInvalidRules() throws Exception {
		// given
		jdbc.update("update games set name = '수정된 이름', description = 'DB 안내' where id = ?",
				UuidBinary.toBytes(COLOR));
		// when
		String body = mvc.perform(headers(get("/api/v1/games/{id}", COLOR), USER, "USER"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		JsonNode data = json.readTree(body).get("data");
		// then
		assertThat(data.get("name").stringValue()).isEqualTo("수정된 이름");
		assertThat(data.get("description").stringValue()).isEqualTo("DB 안내");
		for (String invalid : List.of("{}", RULES.replace("3000", "2999"))) {
			jdbc.update("update games set rules = ? where id = ?", invalid, UuidBinary.toBytes(COLOR));
			mvc.perform(headers(get("/api/v1/games/{id}", COLOR), USER, "USER"))
					.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("COMMON-001"))
					.andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
		}
		assertThat(playCount()).isZero();
	}

	@Test
	@DisplayName("공통 사용자 문맥 헤더와 DB 대조를 실제 API에서도 우회하지 않는다")
	void validatesRealUserContext() throws Exception {
		// given / when / then
		mvc.perform(get("/api/v1/games")).andExpect(status().isUnauthorized());
		mvc.perform(headers(get("/api/v1/games"), USER, "ADMIN")).andExpect(status().isForbidden());
		mvc.perform(headers(get("/api/v1/games"), USER, "USER").header("X-User-Membership", "vip"))
				.andExpect(status().isConflict());
		assertThat(playCount()).isZero();
	}

	private void insert(UUID id, String code, String version, String rules, boolean active, String description) {
		LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
		jdbc.update("""
				insert into games (id, code, name, description, rules, rule_version, is_active, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", UuidBinary.toBytes(id), code, "게임", description, rules, version, active, now, now);
	}

	private int playCount() {
		return jdbc.queryForObject("select count(*) from game_plays where user_id in (?, ?)", Integer.class,
				UuidBinary.toBytes(USER), UuidBinary.toBytes(ADMIN));
	}

	private static MockHttpServletRequestBuilder headers(MockHttpServletRequestBuilder request, UUID user, String role) {
		return request.header("X-User-ID", user).header("X-User-Role", role);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTimeConfiguration {
		@Bean
		@Primary
		Clock gameQueryClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}
}
