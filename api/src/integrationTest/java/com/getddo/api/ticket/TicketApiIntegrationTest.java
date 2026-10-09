package com.getddo.api.ticket;

import java.nio.ByteBuffer;
import java.time.Duration;
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
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import com.getddo.api.support.ApiIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ApiIntegrationTest
class TicketApiIntegrationTest {
	private static final Duration MARGIN = Duration.ofDays(30);
	private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000201");
	private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000202");
	private static final UUID EXPIRED_TICKET = UUID.fromString("00000000-0000-0000-0000-000000000210");
	private static final UUID OLDEST_TICKET = UUID.fromString("00000000-0000-0000-0000-000000000211");
	private static final UUID MIDDLE_TICKET = UUID.fromString("00000000-0000-0000-0000-000000000212");
	private static final UUID NEWEST_TICKET = UUID.fromString("00000000-0000-0000-0000-000000000213");
	private static final UUID OTHER_TICKET = UUID.fromString("00000000-0000-0000-0000-000000000214");
	private static final UUID EXPIRED_GRANT = UUID.fromString("00000000-0000-0000-0000-000000000220");
	private static final UUID OLDEST = UUID.fromString("00000000-0000-0000-0000-000000000221");
	private static final UUID MIDDLE = UUID.fromString("00000000-0000-0000-0000-000000000222");
	private static final UUID NEWEST = UUID.fromString("00000000-0000-0000-0000-000000000223");
	private static final UUID OTHER_GRANT = UUID.fromString("00000000-0000-0000-0000-000000000224");
	private static final Instant T0 = Instant.parse("2026-08-31T01:00:00Z");
	private static final Instant T1 = Instant.parse("2026-09-01T01:00:00Z");
	private static final Instant T2 = Instant.parse("2026-09-02T01:00:00Z");
	private static final Instant T3 = Instant.parse("2026-09-03T01:00:00Z");

	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ObjectMapper mapper;

	/**
	 * 서버는 실제 시계로 만료를 판정한다. 테스트가 월 경계에 걸려도 결과가 바뀌지 않도록 사용 가능 응모권은 지금부터
	 * 30일 뒤, 만료된 응모권은 30일 전에 만료되게 둔다(만료된 응모권의 저장 상태는 AVAILABLE). 본인 응모권 4장
	 * (사용 가능 3장, 만료 1장)과 지급 이력 4건, 타인 응모권 1장을 준비한다.
	 */
	@BeforeEach
	void seed() {
		Instant now = Instant.now();
		insertUser(USER);
		insertUser(OTHER);
		UUID userClaim = insertAttendanceClaim(USER, 4);
		UUID otherClaim = insertAttendanceClaim(OTHER, 1);
		insertTicketWithGrant(EXPIRED_TICKET, EXPIRED_GRANT, USER, userClaim, now.minus(MARGIN), T0);
		insertTicketWithGrant(OLDEST_TICKET, OLDEST, USER, userClaim, now.plus(MARGIN), T1);
		insertTicketWithGrant(MIDDLE_TICKET, MIDDLE, USER, userClaim, now.plus(MARGIN), T2);
		insertTicketWithGrant(NEWEST_TICKET, NEWEST, USER, userClaim, now.plus(MARGIN), T3);
		insertTicketWithGrant(OTHER_TICKET, OTHER_GRANT, OTHER, otherClaim, now.plus(MARGIN), T3);
	}

	@AfterEach
	void clean() {
		for (UUID user : new UUID[] {USER, OTHER}) {
			jdbc.update("delete h from ticket_histories h join tickets t on t.id = h.ticket_id where t.user_id = ?",
					bytes(user));
			jdbc.update("delete from tickets where user_id = ?", bytes(user));
			jdbc.update("delete from attendance_reward_claims where user_id = ?", bytes(user));
			jdbc.update("delete from attendances where user_id = ?", bytes(user));
		}
		jdbc.update("delete from reward_policies where created_by in (?, ?)", bytes(USER), bytes(OTHER));
		jdbc.update("delete from users where id in (?, ?)", bytes(USER), bytes(OTHER));
	}

	@Test
	@DisplayName("T01은 본인의 사용 가능한 응모권만 등급별로 세고 만료 시각이 지난 응모권과 타인 응모권은 뺀다")
	void returnsOwnAvailableTickets() throws Exception {
		// given: seed()가 본인 응모권(사용 가능 3장, 만료 1장)과 타인 응모권을 준비한다.

		// when / then
		mvc.perform(get("/api/v1/tickets/wallets/me").headers(userHeaders(USER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.availableCount").value(3))
				.andExpect(jsonPath("$.data.countByGrade.BRONZE").value(3))
				.andExpect(jsonPath("$.data.countByGrade.SILVER").value(0))
				.andExpect(jsonPath("$.data.countByGrade.GOLD").value(0))
				.andExpect(jsonPath("$.data.holdings.length()").value(1))
				.andExpect(jsonPath("$.data.holdings[0].grade").value("BRONZE"))
				.andExpect(jsonPath("$.data.holdings[0].count").value(3));
	}

	@Test
	@DisplayName("T02는 본인 이력을 최신순 커서로 넘기며 마지막 페이지에서 다음 커서가 없다")
	void pagesOwnHistoryWithCursor() throws Exception {
		// given: seed()가 본인 지급 이력 4건과 타인 지급 이력 1건을 준비한다.

		// when / then
		String body = mvc.perform(get("/api/v1/tickets/ledger/me").headers(userHeaders(USER)).param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(4))
				.andExpect(jsonPath("$.data.items[0].id").value(NEWEST.toString()))
				.andExpect(jsonPath("$.data.items[0].createdAt").value("2026-09-03T01:00:00Z"))
				.andExpect(jsonPath("$.data.items[0].operationType").value("GRANT"))
				.andExpect(jsonPath("$.data.items[0].grade").value("BRONZE"))
				.andExpect(jsonPath("$.data.items[0].status").value("AVAILABLE"))
				.andExpect(jsonPath("$.data.items[1].id").value(MIDDLE.toString()))
				.andReturn().getResponse().getContentAsString();
		String cursor = mapper.readTree(body).path("data").path("nextCursor").asString();
		assertThat(cursor).isNotBlank();
		mvc.perform(get("/api/v1/tickets/ledger/me").headers(userHeaders(USER))
						.param("size", "2").param("cursor", cursor))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[0].id").value(OLDEST.toString()))
				.andExpect(jsonPath("$.data.items[1].id").value(EXPIRED_GRANT.toString()))
				.andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
	}

	@Test
	@DisplayName("T02는 처리 유형과 오프셋이 있는 [from, to) 기간으로 거른다")
	void filtersByTypeAndPeriod() throws Exception {
		// given: seed()가 8/31·9/1·9/2·9/3 01:00Z 지급 이력을 준비한다.

		// when / then
		mvc.perform(get("/api/v1/tickets/ledger/me").headers(userHeaders(USER))
						.param("from", "2026-09-02T10:00:00+09:00").param("to", "2026-09-03T01:00:00Z"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.items[0].id").value(MIDDLE.toString()));
		mvc.perform(get("/api/v1/tickets/ledger/me").headers(userHeaders(USER)).param("transactionType", "USE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(0))
				.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@Test
	@DisplayName("T02의 잘못된 개수·커서·기간은 400 TICKET-004, 해석할 수 없는 값은 400 COMMON-005로 거절한다")
	void rejectsInvalidHistoryQuery() throws Exception {
		// given: seed()가 사용자를 준비한다.

		// when / then
		for (String[] parameter : new String[][] {{"size", "0"}, {"size", "101"}, {"cursor", "bad cursor"}}) {
			mvc.perform(get("/api/v1/tickets/ledger/me").headers(userHeaders(USER)).param(parameter[0], parameter[1]))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("TICKET-004"));
		}
		mvc.perform(get("/api/v1/tickets/ledger/me").headers(userHeaders(USER))
						.param("from", "2026-09-03T00:00:00Z").param("to", "2026-09-02T00:00:00Z"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TICKET-004"));
		mvc.perform(get("/api/v1/tickets/ledger/me").headers(userHeaders(USER)).param("from", "2026-09-01T00:00:00"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
	}

	@Test
	@DisplayName("사용자 헤더가 없거나 등록되지 않은 사용자면 공통 오류로 거절한다")
	void rejectsMissingOrUnknownUser() throws Exception {
		// given: seed()가 사용자를 준비한다.

		// when / then
		mvc.perform(get("/api/v1/tickets/wallets/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER-003"));
		mvc.perform(get("/api/v1/tickets/ledger/me").headers(userHeaders(UUID.randomUUID())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER-002"));
	}

	private HttpHeaders userHeaders(UUID userId) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-User-ID", userId.toString());
		headers.set("X-User-Role", "USER");
		headers.set("X-User-Membership", "vip");
		return headers;
	}

	private void insertUser(UUID id) {
		LocalDateTime now = utc(Instant.now());
		jdbc.update("""
				insert into users (id, name, role, status, membership, updated_at, created_at)
				values (?, 'test', 'USER', 'ACTIVE', 'VIP', ?, ?)
				""", bytes(id), now, now);
	}

	/** 출석 청구 한 건(수량 지정)과 그 부모 행(보상 정책·출석)을 넣는다. */
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

	/** 사용 가능으로 저장된 브론즈 응모권 한 장과 그 지급 이력을 넣는다. 만료 시각만 지정하고 상태는 바꾸지 않는다. */
	private void insertTicketWithGrant(UUID ticketId, UUID historyId, UUID userId, UUID claimId, Instant expiresAt,
			Instant grantedAt) {
		jdbc.update("""
				insert into tickets (id, user_id, attendance_reward_claim_id, grade, status, expires_at, version,
				  created_at, updated_at)
				values (?, ?, ?, 'BRONZE', 'AVAILABLE', ?, 1, ?, ?)
				""", bytes(ticketId), bytes(userId), bytes(claimId), utc(expiresAt), utc(grantedAt), utc(grantedAt));
		jdbc.update("""
				insert into ticket_histories (id, ticket_id, operation_type, ticket_version, status, expires_at, reason,
				  created_at)
				values (?, ?, 'GRANT', 1, 'AVAILABLE', ?, '테스트 지급', ?)
				""", bytes(historyId), bytes(ticketId), utc(expiresAt), utc(grantedAt));
	}

	/** Entity가 Instant를 저장하는 방식과 같게 UTC 기준 날짜·시각으로 넣는다. */
	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
				.putLong(id.getLeastSignificantBits()).array();
	}
}
