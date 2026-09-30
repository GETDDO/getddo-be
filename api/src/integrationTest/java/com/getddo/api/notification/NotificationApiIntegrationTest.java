package com.getddo.api.notification;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.getddo.api.support.ApiIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ApiIntegrationTest
class NotificationApiIntegrationTest {
	private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000101");
	private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000102");
	private static final UUID JOB = UUID.fromString("00000000-0000-0000-0000-000000000103");
	private static final UUID SECOND_JOB = UUID.fromString("00000000-0000-0000-0000-000000000107");
	private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000104");
	private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000105");
	private static final UUID OTHER_NOTIFICATION = UUID.fromString("00000000-0000-0000-0000-000000000106");
	private static final Timestamp NOW = Timestamp.from(Instant.parse("2026-09-30T01:00:00Z"));

	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ObjectMapper mapper;

	@BeforeEach
	void seed() {
		insertUser(USER, "VIP");
		insertUser(OTHER, "EXCELLENT");
		insertJob(JOB);
		insertJob(SECOND_JOB);
		// 같은 발생 건은 사용자당 알림 1건이므로 본인의 두 알림은 서로 다른 작업에 연결한다.
		insertNotification(FIRST, USER, JOB);
		insertNotification(SECOND, USER, SECOND_JOB);
		insertNotification(OTHER_NOTIFICATION, OTHER, JOB);
	}

	@AfterEach
	void clean() {
		jdbc.update("delete from notifications where job_id in (?, ?)", bytes(JOB), bytes(SECOND_JOB));
		jdbc.update("delete from notification_jobs where id in (?, ?)", bytes(JOB), bytes(SECOND_JOB));
		jdbc.update("delete from users where id in (?, ?)", bytes(USER), bytes(OTHER));
	}

	@Test
	@DisplayName("커서 조회는 읽음 상태를 유지하며 본인 알림만 반환한다")
	void cursorListDoesNotReadAndNeverReturnsAnotherUsersNotification() throws Exception {
		// given: seed()가 본인·타인 사용자와 알림을 준비한다.

		// when / then
		String body = mvc.perform(get("/api/v1/notifications/me")
				.headers(userHeaders(USER, "vip")).param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.items[0].id").value(SECOND.toString()))
				.andExpect(jsonPath("$.data.items[0].isRead").value(false))
				.andReturn().getResponse().getContentAsString();
		JsonNode page = mapper.readTree(body).path("data");
		String cursor = page.path("nextCursor").asString();
		assertThat(cursor).isNotBlank();
		mvc.perform(get("/api/v1/notifications/me")
				.headers(userHeaders(USER, "vip")).param("size", "1").param("cursor", cursor))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(FIRST.toString()))
				.andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
		assertThat(unread(USER)).isEqualTo(2);
		assertThat(unread(OTHER)).isEqualTo(1);
	}

	@Test
	@DisplayName("본인만 읽음 처리하며 전체 읽음은 본인 미읽음만 변경한다")
	void onlyOwnerCanMarkReadAndReadAllChangesOnlyUnreadMine() throws Exception {
		// given: seed()가 본인·타인 사용자와 알림을 준비한다.

		// when / then
		mvc.perform(put("/api/v1/notifications/{id}/read", FIRST).headers(userHeaders(OTHER, "excellent")))
				.andExpect(status().isNotFound());
		mvc.perform(put("/api/v1/notifications/{id}/read", FIRST).headers(userHeaders(USER, "vip")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isRead").value(true));
		mvc.perform(get("/api/v1/notifications/me")
				.headers(userHeaders(USER, "vip")).param("isRead", "false"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.items[0].id").value(SECOND.toString()));
		mvc.perform(put("/api/v1/notifications/{id}/read", FIRST).headers(userHeaders(USER, "vip")))
				.andExpect(status().isOk());
		mvc.perform(put("/api/v1/notifications/me/read-all").headers(userHeaders(USER, "vip")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.updatedCount").value(1));
		assertThat(unread(USER)).isZero();
		assertThat(unread(OTHER)).isEqualTo(1);
	}

	@Test
	@DisplayName("사용자 헤더와 멤버십·커서 오류를 계약에 맞게 거절한다")
	void rejectsMissingMalformedAndUnregisteredUserSelection() throws Exception {
		// given: seed()가 본인·타인 사용자와 알림을 준비한다.

		// when / then
		mvc.perform(get("/api/v1/notifications/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER-003"));
		mvc.perform(get("/api/v1/notifications/me").header("X-User-ID", "not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
		mvc.perform(get("/api/v1/notifications/me").headers(userHeaders(UUID.randomUUID(), "vip")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER-002"));
		mvc.perform(get("/api/v1/notifications/me")
				.headers(userHeaders(USER, "excellent")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("USER-006"));
		mvc.perform(get("/api/v1/notifications/me")
				.headers(userHeaders(USER, "vip")))
				.andExpect(status().isOk());
		mvc.perform(get("/api/v1/notifications/me").headers(userHeaders(USER, "vip"))
				.param("cursor", "bad cursor"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("NOTIFICATION-001"));
	}

	@Test
	@DisplayName("멤버십이 없는 관리자도 본인 알림을 조회하고 읽음 처리한다")
	void adminWithoutMembershipUsesCommonUserContext() throws Exception {
		// given
		jdbc.update("update users set role = 'ADMIN', membership = null where id = ?", bytes(USER));
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-User-ID", USER.toString());
		headers.set("X-User-Role", "ADMIN");

		// when / then
		mvc.perform(get("/api/v1/notifications/me").headers(headers))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
		mvc.perform(put("/api/v1/notifications/{id}/read", FIRST).headers(headers))
				.andExpect(status().isOk());
		mvc.perform(put("/api/v1/notifications/me/read-all").headers(headers))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.updatedCount").value(1));
		assertThat(unread(OTHER)).isEqualTo(1);
	}

	@Test
	@DisplayName("공통 사용자 검증을 적용해도 비활성 사용자의 알림 조회를 새로 차단하지 않는다")
	void inactiveUserKeepsAccessToOwnNotifications() throws Exception {
		// given
		jdbc.update("update users set status = 'INACTIVE' where id = ?", bytes(USER));

		// when / then
		mvc.perform(get("/api/v1/notifications/me").headers(userHeaders(USER, "vip")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
	}

	private HttpHeaders userHeaders(UUID userId, String membership) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-User-ID", userId.toString());
		headers.set("X-User-Role", "USER");
		headers.set("X-User-Membership", membership);
		return headers;
	}

	private void insertUser(UUID id, String membership) {
		jdbc.update("""
			insert into users (id, name, role, status, membership, updated_at, created_at)
			values (?, 'test', 'USER', 'ACTIVE', ?, ?, ?)
			""", bytes(id), membership, NOW, NOW);
	}

	/** 서로 다른 발생 건을 구별하는 알림 작업을 준비한다. */
	private void insertJob(UUID jobId) {
		jdbc.update("""
			insert into notification_jobs
			(id, payload, occurrence_key, notification_type, status, scheduled_at, created_at)
			values (?, '{}', ?, 'EVENT_START', 'COMPLETED', ?, ?)
			""", bytes(jobId), "gd65-test-" + jobId, NOW, NOW);
	}

	private void insertNotification(UUID id, UUID userId, UUID jobId) {
		jdbc.update("""
			insert into notifications
			(id, job_id, user_id, title, body, created_at, is_read, mock_delivery_status)
			values (?, ?, ?, '제목', '내용', ?, false, 'SENT')
			""", bytes(id), bytes(jobId), bytes(userId), NOW);
	}

	private long unread(UUID userId) {
		return jdbc.queryForObject("select count(*) from notifications where user_id = ? and is_read = false",
				Long.class, bytes(userId));
	}

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
				.putLong(id.getLeastSignificantBits()).array();
	}
}
