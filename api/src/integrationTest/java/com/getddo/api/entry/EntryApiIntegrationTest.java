package com.getddo.api.entry;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.getddo.api.support.ApiIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 응모 API(E04)와 자격 조회(E07)를 HTTP부터 실제 MySQL까지 전 구간으로 검증한다.
 *
 * <p>서버는 실제 시계로 모집 시간과 응모권 만료를 판정한다. 테스트가 시각 경계에 걸려도 결과가 바뀌지 않도록 모집은 어제부터
 * 내일까지, 응모권은 30일 뒤 만료로 둔다.</p>
 */
@ApiIntegrationTest
class EntryApiIntegrationTest {

	private static final Duration MARGIN = Duration.ofDays(30);
	private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000301");
	private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000302");
	private static final UUID WEIGHTED_EVENT = UUID.fromString("00000000-0000-0000-0000-000000000310");
	private static final UUID UNWEIGHTED_EVENT = UUID.fromString("00000000-0000-0000-0000-000000000311");

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void seed() {
		insertUser(USER, "USER", "VIP");
		insertUser(ADMIN, "ADMIN", null);
		UUID claim = insertAttendanceClaim(USER, 5);
		for (int i = 0; i < 5; i++) {
			insertBronzeTicket(UUID.randomUUID(), claim);
		}
		insertEvent(WEIGHTED_EVENT, true, 5);
		insertEvent(UNWEIGHTED_EVENT, false, 1);
	}

	@AfterEach
	void clean() {
		jdbc.update("delete h from ticket_histories h join tickets t on t.id = h.ticket_id where t.user_id = ?",
				bytes(USER));
		jdbc.update("delete from tickets where user_id = ?", bytes(USER));
		jdbc.update("delete from event_entries where user_id = ?", bytes(USER));
		jdbc.update("delete from event_participants where user_id = ?", bytes(USER));
		jdbc.update("delete from attendance_reward_claims where user_id = ?", bytes(USER));
		jdbc.update("delete from attendances where user_id = ?", bytes(USER));
		jdbc.update("delete from events where id in (?, ?)", bytes(WEIGHTED_EVENT), bytes(UNWEIGHTED_EVENT));
		jdbc.update("delete from reward_policies where created_by = ?", bytes(USER));
		jdbc.update("delete from users where id in (?, ?)", bytes(USER), bytes(ADMIN));
	}

	@Test
	@DisplayName("응모하면 201과 등급별 요청·차감 장수를 반환하고 보유 응모권이 줄며 같은 키로 다시 보내면 200이고 다시 차감하지 않는다")
	void entersAndReplaysWithSameKey() throws Exception {
		// given
		UUID key = UUID.randomUUID();
		// when
		// then
		mvc.perform(enter(WEIGHTED_EVENT, key, "{\"tickets\":{\"BRONZE\":2}}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").value(key.toString()))
				.andExpect(jsonPath("$.data.eventId").value(WEIGHTED_EVENT.toString()))
				.andExpect(jsonPath("$.data.eventTitle").value("응모 API 테스트 이벤트"))
				.andExpect(jsonPath("$.data.requestedTicketCount").value(2))
				.andExpect(jsonPath("$.data.deductedTicketsByGrade.BRONZE").value(2))
				.andExpect(jsonPath("$.data.deductedTicketsByGrade.GOLD").value(0))
				.andExpect(jsonPath("$.data.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.data.rejectionCode").value(nullValue()));
		mvc.perform(get("/api/v1/tickets/me").headers(headers(USER, "USER")))
				.andExpect(jsonPath("$.data.availableCount").value(3));
		mvc.perform(enter(WEIGHTED_EVENT, key, "{\"tickets\":{\"BRONZE\":2}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(key.toString()))
				.andExpect(jsonPath("$.data.deductedTicketsByGrade.BRONZE").value(2));
		mvc.perform(get("/api/v1/tickets/me").headers(headers(USER, "USER")))
				.andExpect(jsonPath("$.data.availableCount").value(3));
		assertThat(count("select count(*) from event_entries where id = ?", bytes(key))).isEqualTo(1);
		assertThat(count("select used_ticket_count from event_participants where user_id = ?", bytes(USER)))
				.isEqualTo(2);
		assertThat(count("select count(*) from ticket_histories where event_entry_id = ? and operation_type = 'USE'",
				bytes(key))).isEqualTo(2);
	}

	@Test
	@DisplayName("같은 키로 다른 내용의 요청을 보내면 409 ENTRY-009이고 응모권은 더 줄지 않는다")
	void sameKeyWithDifferentBodyConflicts() throws Exception {
		// given
		UUID key = UUID.randomUUID();
		mvc.perform(enter(WEIGHTED_EVENT, key, "{\"tickets\":{\"BRONZE\":2}}")).andExpect(status().isCreated());
		// when
		// then
		mvc.perform(enter(WEIGHTED_EVENT, key, "{\"tickets\":{\"BRONZE\":3}}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("ENTRY-009"));
		assertThat(count("select count(*) from tickets where user_id = ? and status = 'SPENT'", bytes(USER)))
				.isEqualTo(2);
	}

	@Test
	@DisplayName("상한 안이라도 보유하지 않은 등급을 고르면 409 TICKET-006이고 가중치 미적용 이벤트에 2장을 보내면 400 ENTRY-001이다")
	void rejectsInsufficientAndInvalidRequests() throws Exception {
		// given
		// when
		// then
		mvc.perform(enter(WEIGHTED_EVENT, UUID.randomUUID(), "{\"tickets\":{\"BRONZE\":4,\"GOLD\":1}}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TICKET-006"));
		mvc.perform(enter(UNWEIGHTED_EVENT, UUID.randomUUID(), "{\"tickets\":{\"BRONZE\":2}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ENTRY-001"));
		assertThat(count("select count(*) from tickets where user_id = ? and status = 'SPENT'", bytes(USER)))
				.isZero();
		assertThat(count("select count(*) from event_participants where user_id = ?", bytes(USER))).isZero();
	}

	@Test
	@DisplayName("가중치 미적용 이벤트는 브론즈 1장으로 한 번만 응모할 수 있고 두 번째는 409 ENTRY-006이다")
	void unweightedEventAllowsSingleBronzeEntry() throws Exception {
		// given
		// when
		// then
		mvc.perform(enter(UNWEIGHTED_EVENT, UUID.randomUUID(), "{\"tickets\":{\"BRONZE\":1}}"))
				.andExpect(status().isCreated());
		mvc.perform(enter(UNWEIGHTED_EVENT, UUID.randomUUID(), "{\"tickets\":{\"BRONZE\":1}}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ENTRY-006"));
	}

	@Test
	@DisplayName("Idempotency-Key가 없거나 UUID가 아니면 400, 사용자 헤더가 없으면 401, 없는 이벤트는 404, 관리자는 403이다")
	void rejectsMissingKeyMissingUserUnknownEventAndAdmin() throws Exception {
		// given
		// when
		// then
		mvc.perform(post("/api/v1/events/{eventId}/entries", WEIGHTED_EVENT).headers(headers(USER, "USER"))
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/events/{eventId}/entries", WEIGHTED_EVENT).headers(headers(USER, "USER"))
						.header("Idempotency-Key", "not-a-uuid")
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
		mvc.perform(post("/api/v1/events/{eventId}/entries", WEIGHTED_EVENT)
						.header("Idempotency-Key", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(enter(UUID.randomUUID(), UUID.randomUUID(), "{\"tickets\":{\"BRONZE\":1}}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ENTRY-004"));
		mvc.perform(post("/api/v1/events/{eventId}/entries", WEIGHTED_EVENT).headers(headers(ADMIN, "ADMIN"))
						.header("Idempotency-Key", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON).content("{\"tickets\":{\"BRONZE\":1}}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ENTRY-002"));
	}

	@Test
	@DisplayName("자격 조회는 응모 전에는 가능, 응모 후에는 사용량과 잔여 상한·보유 장수를 반영한다")
	void eligibilityReflectsEntry() throws Exception {
		// given
		// when
		// then
		mvc.perform(get("/api/v1/events/{eventId}/eligibility", WEIGHTED_EVENT).headers(headers(USER, "USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.eventId").value(WEIGHTED_EVENT.toString()))
				.andExpect(jsonPath("$.data.canEnter").value(true))
				.andExpect(jsonPath("$.data.reasons.length()").value(0))
				.andExpect(jsonPath("$.data.usedTicketCount").value(0))
				.andExpect(jsonPath("$.data.remainingTicketLimit").value(5))
				.andExpect(jsonPath("$.data.availableTicketBalance").value(5));
		mvc.perform(enter(WEIGHTED_EVENT, UUID.randomUUID(), "{\"tickets\":{\"BRONZE\":2}}"))
				.andExpect(status().isCreated());
		mvc.perform(get("/api/v1/events/{eventId}/eligibility", WEIGHTED_EVENT).headers(headers(USER, "USER")))
				.andExpect(jsonPath("$.data.canEnter").value(true))
				.andExpect(jsonPath("$.data.usedTicketCount").value(2))
				.andExpect(jsonPath("$.data.remainingTicketLimit").value(3))
				.andExpect(jsonPath("$.data.availableTicketBalance").value(3));
		mvc.perform(get("/api/v1/events/{eventId}/eligibility", UUID.randomUUID()).headers(headers(USER, "USER")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ENTRY-004"));
	}

	private MockHttpServletRequestBuilder enter(UUID eventId, UUID key, String body) {
		return post("/api/v1/events/{eventId}/entries", eventId).headers(headers(USER, "USER"))
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

	private HttpHeaders headers(UUID userId, String role) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-User-ID", userId.toString());
		headers.set("X-User-Role", role);
		if ("USER".equals(role)) {
			headers.set("X-User-Membership", "vip");
		}
		return headers;
	}

	private void insertUser(UUID id, String role, String membership) {
		LocalDateTime now = utc(Instant.now());
		jdbc.update("""
				insert into users (id, name, role, status, membership, updated_at, created_at)
				values (?, 'test', ?, 'ACTIVE', ?, ?, ?)
				""", bytes(id), role, membership, now, now);
	}

	private void insertEvent(UUID id, boolean weighted, Integer maxTicketsPerUser) {
		Instant now = Instant.now();
		jdbc.update("""
				insert into events (id, title, description, event_type, weighting_enabled, max_tickets_per_user,
				  starts_at, ends_at, status, created_at, updated_at, membership_rule)
				values (?, '응모 API 테스트 이벤트', '설명', 'TICKET', ?, ?, ?, ?, 'OPEN', ?, ?, 'excellent')
				""", bytes(id), weighted, maxTicketsPerUser, utc(now.minus(Duration.ofDays(1))),
				utc(now.plus(Duration.ofDays(1))), utc(now), utc(now));
	}

	/** 출석 청구 한 건과 그 부모 행(보상 정책·출석)을 넣는다. 브론즈 응모권의 지급 근거다. */
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

	private void insertBronzeTicket(UUID ticketId, UUID claimId) {
		LocalDateTime created = utc(Instant.now().minus(Duration.ofDays(1)));
		jdbc.update("""
				insert into tickets (id, user_id, attendance_reward_claim_id, grade, status, expires_at, version,
				  created_at, updated_at)
				values (?, ?, ?, 'BRONZE', 'AVAILABLE', ?, 1, ?, ?)
				""", bytes(ticketId), bytes(USER), bytes(claimId), utc(Instant.now().plus(MARGIN)), created, created);
	}

	private long count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
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
