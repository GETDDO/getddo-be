package com.getddo.api.ticket;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.getddo.api.support.ApiIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 관리자 응모권 조회 API를 실제 MySQL·공통 사용자 문맥·예외 변환까지 연결해 검증한다. */
@ApiIntegrationTest
class AdminTicketApiIntegrationTest {
	private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000301");
	private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000302");
	private static final UUID HEALTHY_TICKET = UUID.fromString("00000000-0000-0000-0000-000000000311");
	private static final UUID BROKEN_TICKET = UUID.fromString("00000000-0000-0000-0000-000000000312");
	private static final UUID HEALTHY_GRANT = UUID.fromString("00000000-0000-0000-0000-000000000321");
	private static final UUID BROKEN_GRANT = UUID.fromString("00000000-0000-0000-0000-000000000322");
	private static final Instant GRANTED = Instant.parse("2026-09-01T01:00:00Z");
	private static final Instant EXPIRES = Instant.parse("2026-12-31T00:00:00Z");

	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;

	/** 관리자 한 명과 일반 사용자 한 명을 만들고, 사용자에게 출석 보상 응모권 두 장(한 장은 현재 상태가 이력과 어긋남)을 준다. */
	@BeforeEach
	void seed() {
		insertUser(ADMIN, "관리자", "ADMIN");
		insertUser(MEMBER, "홍길동", "USER");
		UUID claim = insertAttendanceClaim(MEMBER, 2);
		insertTicketWithGrant(HEALTHY_TICKET, HEALTHY_GRANT, claim, "AVAILABLE", 1);
		insertTicketWithGrant(BROKEN_TICKET, BROKEN_GRANT, claim, "SPENT", 3);
	}

	@AfterEach
	void clean() {
		for (UUID user : new UUID[] {ADMIN, MEMBER}) {
			jdbc.update("delete h from ticket_histories h join tickets t on t.id = h.ticket_id where t.user_id = ?",
					bytes(user));
			jdbc.update("delete from tickets where user_id = ?", bytes(user));
			jdbc.update("delete from attendance_reward_claims where user_id = ?", bytes(user));
			jdbc.update("delete from attendances where user_id = ?", bytes(user));
		}
		jdbc.update("delete from reward_policies where created_by in (?, ?)", bytes(ADMIN), bytes(MEMBER));
		jdbc.update("delete from users where id in (?, ?)", bytes(ADMIN), bytes(MEMBER));
	}

	@Test
	@DisplayName("AO03은 지급 이력을 사용자 이름·근거 종류와 함께 반환하고 이름 검색으로 좁힌다")
	void listsGrants() throws Exception {
		// given: seed()가 출석 보상 응모권 두 장을 지급했다
		// when
		// then
		mvc.perform(admin(get("/api/v1/admin/ticket-grants")).param("keyword", "길동").param("sourceType", "ATTENDANCE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.items[0].userId").value(MEMBER.toString()))
				.andExpect(jsonPath("$.data.items[0].userName").value("홍길동"))
				.andExpect(jsonPath("$.data.items[0].sourceType").value("ATTENDANCE"))
				.andExpect(jsonPath("$.data.items[0].grade").value("BRONZE"));
		mvc.perform(admin(get("/api/v1/admin/ticket-grants")).param("keyword", "없는이름"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	@DisplayName("AO09는 현재 상태가 이력과 어긋난 응모권만 반환하고 AO04는 같은 응모권을 사용자 기준으로 반환한다")
	void reportsInconsistencies() throws Exception {
		// given: BROKEN_TICKET만 버전 3·SPENT로 어긋나 있다
		// when
		// then
		mvc.perform(admin(get("/api/v1/admin/tickets/inconsistencies")).param("userId", MEMBER.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.items[0].ticketId").value(BROKEN_TICKET.toString()))
				.andExpect(jsonPath("$.data.items[0].ticketVersion").value(3))
				.andExpect(jsonPath("$.data.items[0].latestHistoryVersion").value(1))
				.andExpect(jsonPath("$.data.items[0].ticketStatus").value("SPENT"))
				.andExpect(jsonPath("$.data.items[0].latestHistoryStatus").value("AVAILABLE"));
		mvc.perform(admin(get("/api/v1/admin/users/{userId}/tickets/consistency", MEMBER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isConsistent").value(false))
				.andExpect(jsonPath("$.data.availableCount").value(1))
				.andExpect(jsonPath("$.data.inconsistencies.length()").value(1))
				.andExpect(jsonPath("$.data.inconsistencies[0].ticketId").value(BROKEN_TICKET.toString()));
	}

	@Test
	@DisplayName("AO05는 처리 이력에 사용자 이름을 더해 반환하고 AO08은 반환 이력이 없으면 빈 페이지다")
	void listsHistoriesAndEmptyRefunds() throws Exception {
		// given
		// when
		// then
		mvc.perform(admin(get("/api/v1/admin/ticket-histories")).param("userId", MEMBER.toString())
				.param("operationType", "GRANT"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.items[0].userName").value("홍길동"))
				.andExpect(jsonPath("$.data.items[0].operationType").value("GRANT"));
		mvc.perform(admin(get("/api/v1/admin/ticket-refunds")).param("userId", MEMBER.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(0))
				.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	@DisplayName("AO04는 없는 사용자를 404 TICKET-011로 응답한다")
	void unknownUserIsNotFound() throws Exception {
		// given
		// when
		// then
		mvc.perform(admin(get("/api/v1/admin/users/{userId}/tickets/consistency", UUID.randomUUID())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TICKET-011"));
	}

	@Test
	@DisplayName("일반 사용자는 관리자 응모권 조회를 모두 403 TICKET-010으로 거절당한다")
	void nonAdminIsForbidden() throws Exception {
		// given
		// when
		// then
		for (String path : new String[] {"/api/v1/admin/ticket-grants", "/api/v1/admin/ticket-histories",
				"/api/v1/admin/ticket-refunds", "/api/v1/admin/tickets/inconsistencies",
				"/api/v1/admin/users/" + MEMBER + "/tickets/consistency"}) {
			mvc.perform(get(path).header("X-User-ID", MEMBER).header("X-User-Role", "USER")
					.header("X-User-Membership", "vip"))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("TICKET-010"));
		}
	}

	private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
		return request.header("X-User-ID", ADMIN).header("X-User-Role", "ADMIN");
	}

	private void insertUser(UUID id, String name, String role) {
		LocalDateTime now = utc(Instant.now());
		jdbc.update("""
				insert into users (id, name, role, status, membership, updated_at, created_at)
				values (?, ?, ?, 'ACTIVE', ?, ?, ?)
				""", bytes(id), name, role, "USER".equals(role) ? "VIP" : null, now, now);
	}

	private UUID insertAttendanceClaim(UUID userId, int ticketCount) {
		LocalDateTime now = utc(Instant.now());
		UUID policyId = UUID.randomUUID();
		jdbc.update("""
				insert into reward_policies (id, created_by, reward_type, game_id, reward_ticket_count,
				  effective_from, effective_until, created_at)
				values (?, ?, 'ATTENDANCE', null, 1, ?, ?, ?)
				""", bytes(policyId), bytes(userId), now.minusYears(1), now.plusYears(1), now);
		UUID attendanceId = UUID.randomUUID();
		jdbc.update("insert into attendances (id, user_id, attendance_date, created_at) values (?, ?, ?, ?)",
				bytes(attendanceId), bytes(userId), LocalDate.of(2026, 9, 1), now);
		UUID claimId = UUID.randomUUID();
		jdbc.update("""
				insert into attendance_reward_claims (id, user_id, attendance_id, reward_type, reward_policy_id,
				  reward_date, source_key, ticket_count, created_at)
				values (?, ?, ?, 'DAILY', ?, ?, ?, ?, ?)
				""", bytes(claimId), bytes(userId), bytes(attendanceId), bytes(policyId), LocalDate.of(2026, 9, 1),
				"2026-09-01", ticketCount, now);
		return claimId;
	}

	/** 지급 이력(버전 1·AVAILABLE)과 함께, 응모권의 현재 상태·버전을 지정한 값으로 넣는다. */
	private void insertTicketWithGrant(UUID ticketId, UUID historyId, UUID claimId, String status, long version) {
		jdbc.update("""
				insert into tickets (id, user_id, attendance_reward_claim_id, grade, status, expires_at, version,
				  created_at, updated_at)
				values (?, ?, ?, 'BRONZE', ?, ?, ?, ?, ?)
				""", bytes(ticketId), bytes(MEMBER), bytes(claimId), status, utc(EXPIRES), version, utc(GRANTED),
				utc(GRANTED));
		jdbc.update("""
				insert into ticket_histories (id, ticket_id, operation_type, ticket_version, status, expires_at, reason,
				  created_at)
				values (?, ?, 'GRANT', 1, 'AVAILABLE', ?, '테스트 지급', ?)
				""", bytes(historyId), bytes(ticketId), utc(EXPIRES), utc(GRANTED));
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
				.putLong(id.getLeastSignificantBits()).array();
	}
}
