package com.getddo.api.user;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.getddo.api.support.ApiIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 MVC 등록과 GD-51의 MySQL 조회를 U01 응답까지 연결한다. */
@ApiIntegrationTest
class UserContextIntegrationTest {

	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000521");
	private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000522");

	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ObjectMapper mapper;

	@BeforeEach
	void seed() {
		insertUser(USER_ID, "USER", "VIP");
		insertUser(ADMIN_ID, "ADMIN", null);
	}

	@AfterEach
	void clean() {
		jdbc.update("delete from users where id in (unhex(replace(?, '-', '')), unhex(replace(?, '-', '')))",
				USER_ID.toString(), ADMIN_ID.toString());
	}

	@Test
	@DisplayName("일반 사용자와 관리자의 실제 DB 정보를 공통 응답 봉투에 담는다")
	void returnsDatabaseProfiles() throws Exception {
		// given: seed()에서 두 역할의 사용자를 생성한다.
		// when / then
		mvc.perform(get("/api/v1/users/me").header("X-User-ID", USER_ID)
				.header("X-User-Role", "USER").header("X-User-Membership", "vip"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", aMapWithSize(7)))
				.andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
				.andExpect(jsonPath("$.data.name").value("DB 사용자"))
				.andExpect(jsonPath("$.data.role").value("USER"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.membership").value("vip"))
				.andExpect(jsonPath("$.data.phoneNum").value("01001234567"))
				.andExpect(jsonPath("$.data.email").value(nullValue()));
		mvc.perform(get("/api/v1/users/me").header("X-User-ID", ADMIN_ID).header("X-User-Role", "ADMIN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(ADMIN_ID.toString()))
				.andExpect(jsonPath("$.data.role").value("ADMIN"))
				.andExpect(jsonPath("$.data.membership").value(nullValue()));
	}

	@Test
	@DisplayName("다음 HTTP 요청은 변경된 DB 멤버십을 다시 조회한다")
	void nextRequestReadsChangedMembership() throws Exception {
		// given
		mvc.perform(get("/api/v1/users/me").header("X-User-ID", USER_ID)
				.header("X-User-Role", "USER").header("X-User-Membership", "vip"))
				.andExpect(status().isOk());
		jdbc.update("update users set membership = 'VVIP' where id = unhex(replace(?, '-', ''))", USER_ID.toString());
		// when / then
		mvc.perform(get("/api/v1/users/me").header("X-User-ID", USER_ID)
				.header("X-User-Role", "USER").header("X-User-Membership", "vip"))
				.andExpect(status().isConflict());
		mvc.perform(get("/api/v1/users/me").header("X-User-ID", USER_ID)
				.header("X-User-Role", "USER").header("X-User-Membership", "vvip"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.membership").value("vvip"));
	}

	@Test
	@DisplayName("DB 역할 불일치와 일반 사용자의 멤버십 부재를 거절한다")
	void rejectsInvalidDatabaseContext() throws Exception {
		// given / when / then
		mvc.perform(get("/api/v1/users/me").header("X-User-ID", USER_ID).header("X-User-Role", "ADMIN"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER-004"));
		jdbc.update("update users set membership = null where id = unhex(replace(?, '-', ''))", USER_ID.toString());
		mvc.perform(get("/api/v1/users/me").header("X-User-ID", USER_ID)
				.header("X-User-Role", "USER").header("X-User-Membership", "vip"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER-005"));
	}

	@Test
	@DisplayName("실제 INACTIVE 사용자와 미등록 ID를 계약대로 거절한다")
	void rejectsInactiveAndUnknownUsers() throws Exception {
		// given
		jdbc.update("update users set status = 'INACTIVE' where id = unhex(replace(?, '-', ''))", USER_ID.toString());
		// when / then
		mvc.perform(get("/api/v1/users/me").header("X-User-ID", USER_ID)
				.header("X-User-Role", "USER").header("X-User-Membership", "vip"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER-007"));
		mvc.perform(get("/api/v1/users/me").header("X-User-ID", UUID.randomUUID())
				.header("X-User-Role", "USER").header("X-User-Membership", "vip"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER-002"));
	}

	@Test
	@DisplayName("Swagger는 사용자 헤더 없이 열리고 U01의 헤더 3개를 문서화한다")
	void documentsHeadersWithoutExposingDomainParameters() throws Exception {
		// given / when
		String body = mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		JsonNode parameters = mapper.readTree(body).path("paths").path("/api/v1/users/me")
				.path("get").path("parameters");
		List<String> names = new ArrayList<>();
		// then
		for (JsonNode parameter : parameters) {
			assertThat(parameter.path("in").asString()).isEqualTo("header");
			names.add(parameter.path("name").asString());
		}
		assertThat(names).containsExactlyInAnyOrder("X-User-ID", "X-User-Role", "X-User-Membership");
	}

	private void insertUser(UUID id, String role, String membership) {
		jdbc.update("""
				insert into users (id, name, role, status, membership, phone_num, created_at, updated_at)
				values (unhex(replace(?, '-', '')), 'DB 사용자', ?, 'ACTIVE', ?, ?,
				        '2026-09-30 01:00:00', '2026-09-30 01:00:00')
				""", id.toString(), role, membership, "USER".equals(role) ? "01001234567" : null);
	}
}
