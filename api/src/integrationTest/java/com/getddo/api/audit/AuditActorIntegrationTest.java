package com.getddo.api.audit;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import com.getddo.api.support.ApiIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ApiIntegrationTest
@Import(AuditActorTestFixtures.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuditActorIntegrationTest {

	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ObjectMapper mapper;
	private UUID userId;
	private UUID adminId;
	private UUID targetId;

	@BeforeEach
	void seed() {
		userId = UUID.randomUUID();
		adminId = UUID.randomUUID();
		targetId = UUID.randomUUID();
		insertUser(userId, "USER", "VIP");
		insertUser(adminId, "ADMIN", null);
	}

	@AfterEach
	void clean() {
		jdbc.update("delete from audit_logs where target_id = unhex(replace(?, '-', ''))", targetId.toString());
		jdbc.update("delete from users where id in (unhex(replace(?, '-', '')), unhex(replace(?, '-', '')))",
				userId.toString(), adminId.toString());
	}

	@Test
	@DisplayName("사용자를 바꾸면 실제 DB 처리자도 바뀌고 본문의 actorId·role로 덮어쓰지 못한다")
	void recordsSelectedUserInsteadOfBodyActor() throws Exception {
		// given / when / then
		assertRecordedActor(userId, "USER", adminId, "ADMIN");
		assertRecordedActor(adminId, "ADMIN", userId, "USER");
		assertThat(logCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("사용자 헤더 누락·DB 역할 불일치 요청은 감사 기록을 만들지 않는다")
	void rejectsInvalidContextBeforeRecording() throws Exception {
		// given
		String body = body(adminId, "ADMIN");
		// when / then
		mvc.perform(post("/test/audit/record").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER-003"));
		mvc.perform(post("/test/audit/record").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("X-User-ID", userId).header("X-User-Role", "ADMIN"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER-004"));
		assertThat(logCount()).isZero();
	}

	private void assertRecordedActor(UUID selected, String role, UUID bodyActor, String bodyRole) throws Exception {
		String response = mvc.perform(post("/test/audit/record")
				.header("X-User-ID", selected).header("X-User-Role", role).header("X-User-Membership", "vip")
				.contentType(MediaType.APPLICATION_JSON).content(body(bodyActor, bodyRole)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
				.andReturn().getResponse().getContentAsString();
		UUID auditId = UUID.fromString(mapper.readTree(response).path("data").asString());
		assertThat(jdbc.queryForObject("""
				select lower(hex(actor_id)) from audit_logs
				where id = unhex(replace(?, '-', '')) and target_id = unhex(replace(?, '-', ''))
				""", String.class, auditId.toString(), targetId.toString()))
				.isEqualTo(selected.toString().replace("-", ""));
		assertThat(jdbc.queryForObject("""
				select json_length(after_data) from audit_logs where id = unhex(replace(?, '-', ''))
				""", Integer.class, auditId.toString())).isEqualTo(1);
	}

	private String body(UUID actorId, String role) {
		return """
				{"targetId":"%s","actorId":"%s","role":"%s"}
				""".formatted(targetId, actorId, role);
	}

	private void insertUser(UUID id, String role, String membership) {
		jdbc.update("""
				insert into users (id, name, role, status, membership, created_at, updated_at)
				values (unhex(replace(?, '-', '')), '감사 테스트', ?, 'ACTIVE', ?,
				        '2026-10-01 00:00:00', '2026-10-01 00:00:00')
				""", id.toString(), role, membership);
	}

	private int logCount() {
		return jdbc.queryForObject("select count(*) from audit_logs where target_id = unhex(replace(?, '-', ''))",
				Integer.class, targetId.toString());
	}
}
