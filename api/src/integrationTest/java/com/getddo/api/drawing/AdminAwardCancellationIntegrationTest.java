package com.getddo.api.drawing;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import com.getddo.api.support.ApiIntegrationTest;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.service.DrawConfirmationService;
import com.getddo.core.drawing.service.DrawSnapshotService;

import static com.getddo.db.common.util.UuidBinary.toBytes;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 HTTP·취소 등록·GD-87 준비/선정·MySQL을 연결한다. 폴링 대신 요청 실행을 검증한다. */
@ApiIntegrationTest
@TestPropertySource(properties = "getddo.drawing.retry-worker.enabled=false")
class AdminAwardCancellationIntegrationTest {
	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private TimeProvider time;
	@Autowired private DrawSnapshotService snapshots;
	@Autowired private DrawConfirmationService confirmation;
	private final JsonMapper json = JsonMapper.builder().build();
	private UUID actor;
	private UUID event;
	private UUID win;
	private Instant now;

	@BeforeEach void setUp() {
		now = time.now();
		actor = user("ADMIN");
		event = UUID.randomUUID();
		jdbc.update("""
			insert into events (id, title, description, image_key, event_type, weighting_enabled, starts_at, ends_at,
			status, membership_rule, created_at, updated_at) values (?, 'HTTP 취소', '설명', 'test.png', 'NO_TICKET', false, ?, ?, 'CLOSED', 'vip', ?, ?)
			""", toBytes(event), utc(now.minusSeconds(3600)), utc(now.minusSeconds(301)), utc(now), utc(now));
		jdbc.update("insert into event_prizes (id, event_id, prize_rank, name, winner_count, created_at, updated_at) values (?, ?, 1, '경품', 1, ?, ?)",
				toBytes(UUID.randomUUID()), toBytes(event), utc(now), utc(now));
		for (int index = 0; index < 2; index++) {
			UUID owner = user("USER"), participant = UUID.randomUUID();
			jdbc.update("insert into event_participants (id, event_id, user_id, used_ticket_count, created_at) values (?, ?, ?, 0, ?)",
					toBytes(participant), toBytes(event), toBytes(owner), utc(now.minusSeconds(302)));
			jdbc.update("insert into event_entries (id, participant_id, user_id, requested_ticket_count, deducted_ticket_count, created_at) values (?, ?, ?, 0, 0, ?)",
					toBytes(UUID.randomUUID()), toBytes(participant), toBytes(owner), utc(now.minusSeconds(302)));
		}
		DrawSnapshot initial = snapshots.prepareInitial(event);
		win = confirmation.confirm(initial.runId()).getResults().getFirst().getId();
	}

	@Test @DisplayName("AD05 실제 등록·서버 실행과 AD09 재요청이 같은 DB 실행·결과를 반환한다")
	void cancellationAndRedrawReplayUseSameConfirmedRun() throws Exception {
		// given
		UUID key = UUID.randomUUID();
		// when
		String first = mvc.perform(post(cancelPath(win)).header("X-User-ID", actor).header("X-User-Role", "ADMIN")
				.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"운영 사유\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"))
				.andReturn().getResponse().getContentAsString();
		String cancellationId = json.readTree(first).get("data").get("cancellationId").asText();
		String runId = json.readTree(first).get("data").get("replacementDrawRunId").asText();
		String stored = mvc.perform(post(redrawPath(cancellationId)).header("X-User-ID", actor).header("X-User-Role", "ADMIN"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.runId").value(runId))
				.andExpect(jsonPath("$.data.confirmation.results[0].resultType").value("SELECTED"))
				.andReturn().getResponse().getContentAsString();
		// then
		mvc.perform(post(redrawPath(cancellationId)).header("X-User-ID", actor).header("X-User-Role", "ADMIN"))
				.andExpect(status().isOk()).andExpect(content().json(stored));
		mvc.perform(post(cancelPath(win)).header("X-User-ID", actor).header("X-User-Role", "ADMIN")
				.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"운영 사유\"}"))
				.andExpect(status().isOk()).andExpect(content().json(first));
		assertThat(jdbc.queryForObject("select count(*) from draw_runs where event_id = ?", Long.class, toBytes(event))).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from award_cancellations where draw_result_id = ?", Long.class, toBytes(win))).isEqualTo(1);
	}

	@Test @DisplayName("실제 비관리자·비활성 관리자와 잘못된 입력·없는 대상은 승인된 오류로 거절한다")
	void rejectsInvalidActorsAndRequestsWithoutCreatingRuns() throws Exception {
		// given / when / then
		UUID normalUser = user("USER"), inactive = user("ADMIN"), key = UUID.randomUUID();
		jdbc.update("update users set status = 'INACTIVE' where id = ?", toBytes(inactive));
		mvc.perform(post(cancelPath(win)).header("X-User-ID", normalUser).header("X-User-Role", "USER")
				.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"사유\"}"))
				.andExpect(status().isForbidden());
		mvc.perform(post(cancelPath(win)).header("X-User-ID", inactive).header("X-User-Role", "ADMIN")
				.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"사유\"}"))
				.andExpect(status().isForbidden());
		mvc.perform(post(cancelPath(win)).header("X-User-ID", actor).header("X-User-Role", "ADMIN")
				.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post(cancelPath(UUID.randomUUID())).header("X-User-ID", actor).header("X-User-Role", "ADMIN")
				.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"사유\"}"))
				.andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("select count(*) from draw_runs where event_id = ?", Long.class, toBytes(event))).isEqualTo(1);
	}

	@Test @DisplayName("실제 멱등 키의 사유 충돌과 다른 키의 중복 취소는 409로 반환한다")
	void rejectsIdempotencyAndDuplicateCancellationConflicts() throws Exception {
		// given
		UUID key = UUID.randomUUID();
		mvc.perform(post(cancelPath(win)).header("X-User-ID", actor).header("X-User-Role", "ADMIN")
				.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"사유\"}"))
				.andExpect(status().isOk());
		// when / then
		mvc.perform(post(cancelPath(win)).header("X-User-ID", actor).header("X-User-Role", "ADMIN")
				.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"다른 사유\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANCEL-003"));
		mvc.perform(post(cancelPath(win)).header("X-User-ID", actor).header("X-User-Role", "ADMIN")
				.header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"사유\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANCEL-002"));
	}

	private UUID user(String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into users (id, name, role, status, membership, created_at, updated_at) values (?, 'HTTP 사용자', ?, 'ACTIVE', 'VIP', ?, ?)",
				toBytes(id), role, utc(now), utc(now));
		return id;
	}
	private static String cancelPath(UUID id) { return "/api/v1/admin/wins/" + id + "/cancellations"; }
	private static String redrawPath(String id) { return "/api/v1/admin/cancellations/" + id + "/redraws"; }
	private static LocalDateTime utc(Instant instant) { return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }
}
