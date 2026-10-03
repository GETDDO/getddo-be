package com.getddo.api.attendance;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ApiIntegrationTest
class AttendanceApiIntegrationTest {
	private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000301");
	private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000302");
	private static final UUID DAILY_POLICY = UUID.fromString("00000000-0000-0000-0000-000000000311");
	private static final UUID STREAK_SET = UUID.fromString("00000000-0000-0000-0000-000000000312");
	/** 정책 적용 시작과 등록 시각. 실제 서버 시계보다 항상 과거다. */
	private static final LocalDateTime SEED_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);

	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ObjectMapper mapper;

	@BeforeEach
	void seedUsers() {
		insertUser(USER, "USER", "VIP");
		insertUser(ADMIN, "ADMIN", null);
	}

	@AfterEach
	void clean() {
		byte[] user = bytes(USER);
		jdbc.update("""
				delete a from ticket_ledger_allocations a join ticket_ledger l on l.id = a.ledger_id
				where l.user_id = ?
				""", user);
		jdbc.update("delete from ticket_ledger where user_id = ?", user);
		jdbc.update("delete from ticket_wallets where user_id = ?", user);
		jdbc.update("delete from attendance_reward_claims where user_id = ?", user);
		jdbc.update("delete from attendance_streaks where user_id = ?", user);
		jdbc.update("delete from attendances where user_id = ?", user);
		jdbc.update("delete from attendance_streak_policies where policy_set_id = ?", bytes(STREAK_SET));
		jdbc.update("delete from attendance_streak_policy_sets where id = ?", bytes(STREAK_SET));
		jdbc.update("delete from reward_policies where id = ?", bytes(DAILY_POLICY));
		jdbc.update("delete from users where id in (?, ?)", user, bytes(ADMIN));
	}

	@Test
	@DisplayName("첫 출석은 201로 일일 보상을 확정하고 같은 날 재요청은 200으로 같은 결과를 돌려주며 다시 지급하지 않는다")
	void firstAttendanceCreatesAndRepeatReturnsSameResult() throws Exception {
		// given
		seedPolicies();
		String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();

		// when / then
		String first = mvc.perform(post("/api/v1/attendances").headers(userHeaders(USER)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.attendanceDate").value(today))
				.andExpect(jsonPath("$.data.consecutiveDays").value(1))
				.andExpect(jsonPath("$.data.rewards.length()").value(1))
				.andExpect(jsonPath("$.data.rewards[0].ticketCount").value(2))
				.andReturn().getResponse().getContentAsString();
		JsonNode created = mapper.readTree(first).path("data");

		String second = mvc.perform(post("/api/v1/attendances").headers(userHeaders(USER)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		JsonNode repeated = mapper.readTree(second).path("data");

		assertThat(repeated).isEqualTo(created);
		assertThat(count("select count(*) from attendances where user_id = ?")).isEqualTo(1);
		assertThat(count("select count(*) from ticket_ledger where user_id = ?")).isEqualTo(1);
		assertThat(count("select coalesce(sum(balance), 0) from ticket_wallets where user_id = ?")).isEqualTo(2);
	}

	@Test
	@DisplayName("적용할 출석 정책이 없으면 500 ATTENDANCE-001로 응답하고 출석을 남기지 않는다")
	void missingPolicyFailsWithoutRows() throws Exception {
		// given: 정책을 넣지 않는다.

		// when / then
		mvc.perform(post("/api/v1/attendances").headers(userHeaders(USER)))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("ATTENDANCE-001"));
		assertThat(count("select count(*) from attendances where user_id = ?")).isZero();
		assertThat(count("select count(*) from ticket_ledger where user_id = ?")).isZero();
	}

	@Test
	@DisplayName("사용자 헤더가 없거나 등록되지 않은 사용자면 출석하지 않고 401로 응답한다")
	void rejectsMissingOrUnknownUser() throws Exception {
		// given
		seedPolicies();

		// when / then
		mvc.perform(post("/api/v1/attendances"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER-003"));
		mvc.perform(post("/api/v1/attendances").headers(userHeaders(UUID.randomUUID())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER-002"));
		assertThat(count("select count(*) from attendances where user_id = ?")).isZero();
	}

	/** 종료 없는 일일 정책(2장)과 7·14·28일 단계 정책 묶음을 넣는다. 둘 다 DB 전체에서 하나만 둘 수 있다. */
	private void seedPolicies() {
		jdbc.update("""
				insert into reward_policies (id, created_by, reward_type, game_id, reward_ticket_count,
				  effective_from, effective_until, created_at)
				values (?, ?, 'ATTENDANCE', null, 2, ?, null, ?)
				""", bytes(DAILY_POLICY), bytes(ADMIN), SEED_TIME, SEED_TIME);
		jdbc.update("""
				insert into attendance_streak_policy_sets (id, created_by, effective_month, created_at)
				values (?, ?, ?, ?)
				""", bytes(STREAK_SET), bytes(ADMIN), LocalDate.of(2026, 1, 1), SEED_TIME);
		int[][] milestones = {{7, 1}, {14, 3}, {28, 7}};
		for (int[] milestone : milestones) {
			jdbc.update("""
					insert into attendance_streak_policies (id, policy_set_id, milestone_days, reward_ticket_count,
					  created_at)
					values (?, ?, ?, ?, ?)
					""", bytes(UUID.randomUUID()), bytes(STREAK_SET), milestone[0], milestone[1], SEED_TIME);
		}
	}

	private long count(String sql) {
		return jdbc.queryForObject(sql, Long.class, bytes(USER));
	}

	private HttpHeaders userHeaders(UUID userId) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-User-ID", userId.toString());
		headers.set("X-User-Role", "USER");
		headers.set("X-User-Membership", "vip");
		return headers;
	}

	private void insertUser(UUID id, String role, String membership) {
		jdbc.update("""
				insert into users (id, name, role, status, membership, updated_at, created_at)
				values (?, 'test', ?, 'ACTIVE', ?, ?, ?)
				""", bytes(id), role, membership, SEED_TIME, SEED_TIME);
	}

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
				.putLong(id.getLeastSignificantBits()).array();
	}
}
