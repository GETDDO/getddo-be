package com.getddo.api.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.getddo.api.support.ApiIntegrationTest;
import com.getddo.core.notification.domain.NotificationJobRequest;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.service.NotificationJobService;
import com.getddo.db.common.util.UuidBinary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ApiIntegrationTest
class AdminEventModificationTest {
	private static final Instant START = Instant.parse("2099-10-12T09:00:00Z");
	private static final Instant END = START.plusSeconds(7200);
	private static final Instant CREATED = Instant.parse("2026-10-01T00:00:00Z");
	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private NotificationJobService jobs;
	private UUID admin;
	private UUID event;
	private UUID firstPrize;
	private UUID secondPrize;
	private final List<UUID> fixtureEvents = new ArrayList<>();
	private final List<UUID> fixtureUsers = new ArrayList<>();

	@BeforeEach
	void setUp() {
		admin = user("ADMIN");
		event = event();
		firstPrize = prize(event, 1, "첫 경품");
		secondPrize = prize(event, 2, "둘째 경품");
	}

	@AfterEach
	void cleanUp() {
		// 동시 요청은 서로 다른 트랜잭션에서 커밋하므로 이 테스트가 만든 행만 FK 순서로 정리한다.
		for (UUID fixture : fixtureEvents) {
			jdbc.update("delete from notifications where event_id=?", bytes(fixture));
			jdbc.update("update notification_jobs set source_job_id=null where event_id=?", bytes(fixture));
			jdbc.update("delete from notification_jobs where event_id=?", bytes(fixture));
			jdbc.update("delete from event_prizes where event_id=?", bytes(fixture));
			jdbc.update("delete from events where id=?", bytes(fixture));
		}
		fixtureUsers.forEach(fixture -> jdbc.update("delete from users where id=?", bytes(fixture)));
	}

	@Test
	@DisplayName("설정과 경품 등수를 함께 바꾸며 기존 ID·생성 시각과 최종 등수 정렬을 유지한다")
	void updatesSettingsAndSwapsRanksWithoutChangingPrizeIds() throws Exception {
		// given
		String body = body(START, "수정 이벤트", prizeJson(firstPrize, 2, "첫 수정"), prizeJson(secondPrize, 1, "둘째 수정"));
		// when / then
		modify(body).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("수정 이벤트"))
				.andExpect(jsonPath("$.data.description").value("설명"))
				.andExpect(jsonPath("$.data.imageKey").value("events/edited.png"))
				.andExpect(jsonPath("$.data.prizes[0].winnerCount").value(2))
				.andExpect(jsonPath("$.data.status").value("SCHEDULED"))
				.andExpect(jsonPath("$.data.prizes[0].id").value(secondPrize.toString()))
				.andExpect(jsonPath("$.data.prizes[1].id").value(firstPrize.toString()));
		assertThat(jdbc.queryForObject("select created_at from event_prizes where id=?", LocalDateTime.class, bytes(firstPrize)))
				.isEqualTo(utc(CREATED));
		assertThat(jobsCount()).isZero();
	}

	@Test
	@DisplayName("누락 경품을 제거하고 새 경품을 추가하며 높은 등수도 임시 등수 오버플로 없이 수정한다")
	void replacesPrizesAndSupportsMaximumRank() throws Exception {
		// given / when
		String response = modify(body(START, "수정", prizeJson(firstPrize, Integer.MAX_VALUE, "유지"), prizeJson(null, 1, "신규")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.prizes.length()").value(2))
				.andExpect(jsonPath("$.data.prizes[1].id").value(firstPrize.toString()))
				.andReturn().getResponse().getContentAsString();
		// then
		String newId = com.jayway.jsonpath.JsonPath.read(response, "$.data.prizes[0].id");
		assertThat(UUID.fromString(newId).version()).isEqualTo(7);
		assertThat(jdbc.queryForObject("select count(*) from event_prizes where id=?", Long.class, bytes(secondPrize))).isZero();
		assertThat(jdbc.queryForObject("select count(*) from event_prizes where event_id=?", Long.class, bytes(event))).isEqualTo(2);
	}

	@Test
	@DisplayName("다른 이벤트의 경품 ID를 전달하면 원본 이벤트·경품이 바뀌지 않는다")
	void rejectsAnotherEventsPrize() throws Exception {
		// given
		UUID otherPrize = prize(event(), 1, "타 이벤트 경품");
		// when / then
		modify(body(START, "거절", prizeJson(otherPrize, 1, "경품")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EVENT-004"));
		assertOriginal();
	}

	@ParameterizedTest
	@ValueSource(strings = {"기준 이벤트", "제목 변경"})
	@DisplayName("같은 내용의 ID 없는 경품도 신규 등록하며 응답 ID로 다시 수정하면 그 ID를 유지한다")
	void missingPrizeIdCreatesNewPrizeRegardlessOfTitleChange(String title) throws Exception {
		// given
		modify(body(START, "기준 이벤트", prizeJson(firstPrize, 1, "첫 경품"), prizeJson(secondPrize, 2, "둘째 경품")))
				.andExpect(status().isOk());
		String request = body(START, title, prizeJson(null, 1, "첫 경품"), prizeJson(secondPrize, 2, "둘째 경품"));
		// when
		String response = modify(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		UUID newId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(response, "$.data.prizes[0].id"));
		// then
		assertThat(newId).isNotEqualTo(firstPrize);
		assertThat(newId.version()).isEqualTo(7);
		assertThat(jdbc.queryForObject("select count(*) from event_prizes where id=?", Long.class, bytes(firstPrize))).isZero();
		assertThat(jdbc.queryForObject("select count(*) from event_prizes where event_id=?", Long.class, bytes(event))).isEqualTo(2);
		modify(body(START, title, prizeJson(newId, 1, "첫 경품"), prizeJson(secondPrize, 2, "둘째 경품")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.prizes[0].id").value(newId.toString()))
				.andExpect(jsonPath("$.data.prizes[1].id").value(secondPrize.toString()));
		String repeated = modify(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(com.jayway.jsonpath.JsonPath.<String>read(repeated, "$.data.prizes[0].id")).isNotEqualTo(newId.toString());
		assertThat(jdbc.queryForObject("select count(*) from event_prizes where id=?", Long.class, bytes(newId))).isZero();
		assertThat(jobsCount()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {"OPEN", "CLOSED", "DRAW_CONFIRMED", "PUBLISHED", "CANCELED", "REDRAWING", "NO_ENTRANTS"})
	@DisplayName("시작 시각 전이라도 진행 예정 이외의 상태는 수정하지 않는다")
	void rejectsNonScheduledState(String state) throws Exception {
		// given
		jdbc.update("update events set status=? where id=?", state, bytes(event));
		// when / then
		modify(body(START, "거절", prizeJson(firstPrize, 1, "수정")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT-011"));
		assertOriginal();
	}

	@Test
	@DisplayName("시작 시각이 지난 SCHEDULED·앞당김·삭제 이벤트와 잘못된 UUID를 각각 거절한다")
	void rejectsTimeBoundaryEarlierStartDeletedEventAndMalformedId() throws Exception {
		// given / when / then
		String request = body(START, "거절", prizeJson(firstPrize, 1, "수정"));
		modify(body(START.minusSeconds(1), "거절", prizeJson(firstPrize, 1, "수정")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT-012"));
		jdbc.update("update events set starts_at=? where id=?", utc(CREATED), bytes(event));
		modify(request).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT-011"));
		jdbc.update("update events set starts_at=?, deleted_at=? where id=?", utc(START), utc(CREATED), bytes(event));
		modify(request).andExpect(status().isNotFound());
		mvc.perform(put("/api/v1/admin/events/invalid").header("X-User-ID", admin.toString()).header("X-User-Role", "ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("중복 경품 ID·등수와 TEXT 바이트 상한 초과는 DB 저장 전 400으로 거절한다")
	void validatesPrizeIdsRanksAndUtf8ByteLimit() throws Exception {
		// given / when / then
		modify(body(START, "거절", prizeJson(firstPrize, 1, "경품"), prizeJson(firstPrize, 2, "경품")))
				.andExpect(status().isBadRequest());
		modify(body(START, "거절", prizeJson(firstPrize, 1, "경품"), prizeJson(secondPrize, 1, "경품")))
				.andExpect(status().isBadRequest());
		String overlong = body(START, "거절", prizeJson(firstPrize, 1, "경품"))
				.replace("\"description\": \"설명\"", "\"description\": \"" + "😀".repeat(16_384) + "\"");
		modify(overlong).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EVENT-001"));
		assertOriginal();
	}

	@Test
	@DisplayName("일반 사용자와 미등록 요청자는 관리자 수정 API를 사용할 수 없다")
	void requiresAnActiveAdministrator() throws Exception {
		// given
		UUID ordinary = user("USER");
		String request = body(START, "거절", prizeJson(firstPrize, 1, "경품"));
		// when / then
		mvc.perform(put("/api/v1/admin/events/" + event).header("X-User-ID", ordinary.toString())
				.header("X-User-Role", "USER").header("X-User-Membership", "vip")
				.contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isForbidden());
		mvc.perform(put("/api/v1/admin/events/" + event).header("X-User-ID", UUID.randomUUID().toString())
				.header("X-User-Role", "ADMIN").contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isUnauthorized());
		assertOriginal();
	}

	@Test
	@DisplayName("일정 연기는 기존 시작 작업을 멈추고 실제 수신자를 고정하며 PUT 재요청에도 안내를 중복 등록하지 않는다")
	void linksScheduleChangeAndKeepsRepeatedPutIdempotent() throws Exception {
		// given
		UUID recipient = user("USER");
		UUID startJob = startJob(recipient);
		String request = body(START.plusSeconds(600), "변경 이벤트", prizeJson(firstPrize, 1, "첫 수정"), prizeJson(null, 2, "신규"));
		// when
		String first = modify(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		UUID newPrizeId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(first, "$.data.prizes[1].id"));
		String repeated = modify(body(START.plusSeconds(600), "변경 이벤트",
				prizeJson(firstPrize, 1, "첫 수정"), prizeJson(newPrizeId, 2, "신규")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		// then
		assertThat(jobsCount()).isEqualTo(1);
		assertThat(com.jayway.jsonpath.JsonPath.<String>read(repeated, "$.data.prizes[1].id"))
				.isEqualTo(com.jayway.jsonpath.JsonPath.<String>read(first, "$.data.prizes[1].id"));
		assertThat(jdbc.queryForObject("select json_unquote(json_extract(payload,'$.generationStoppedBy')) from notification_jobs where id=?",
				String.class, bytes(startJob))).startsWith("event-change:");
		String payload = jdbc.queryForObject("select cast(payload as char) from notification_jobs where event_id=? and notification_type='EVENT_SCHEDULE_CHANGED'",
				String.class, bytes(event));
		assertThat(payload).contains(recipient.toString()).contains("변경 이벤트");
		assertThat(jdbc.queryForObject("select count(*) from notifications where job_id=?", Long.class, bytes(startJob))).isEqualTo(1);
	}

	@Test
	@DisplayName("동시 PUT의 ID 없는 경품은 각각 새로 등록하지만 같은 일정 변경 안내는 한 번만 등록한다")
	void concurrentIdenticalPutsRegisterOneChange() throws Exception {
		// given
		String request = body(START.plusSeconds(600), "동시 수정", prizeJson(firstPrize, 1, "유지"), prizeJson(null, 2, "새 경품"));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Callable<MvcResult> call = () -> {
				ready.countDown();
				if (!start.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("동시 요청 준비 실패"); }
				return modify(request).andReturn();
			};
			Future<MvcResult> first = executor.submit(call);
			Future<MvcResult> second = executor.submit(call);
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			// when
			start.countDown();
			// then
			MvcResult firstResult = first.get(30, TimeUnit.SECONDS);
			MvcResult secondResult = second.get(30, TimeUnit.SECONDS);
			assertThat(firstResult.getResponse().getStatus()).as("첫 요청: %s", firstResult.getResolvedException()).isEqualTo(200);
			assertThat(secondResult.getResponse().getStatus()).as("두 번째 요청: %s", secondResult.getResolvedException()).isEqualTo(200);
			String firstNewId = com.jayway.jsonpath.JsonPath.read(firstResult.getResponse().getContentAsString(), "$.data.prizes[1].id");
			String secondNewId = com.jayway.jsonpath.JsonPath.read(secondResult.getResponse().getContentAsString(), "$.data.prizes[1].id");
			assertThat(firstNewId).isNotEqualTo(secondNewId);
			assertThat(jdbc.queryForObject("select count(*) from event_prizes where id in (?,?)", Long.class,
					bytes(UUID.fromString(firstNewId)), bytes(UUID.fromString(secondNewId)))).isEqualTo(1);
		}
		assertThat(jobsCount()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from event_prizes where event_id=?", Long.class, bytes(event))).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from event_prizes where id=?", Long.class, bytes(firstPrize))).isEqualTo(1);
	}

	@Test
	@DisplayName("경품 DB 저장 실패는 이벤트 설정·기존 ID·등수와 변경 안내를 모두 원복한다")
	void prizeFailureRollsBackEventAndRanks() throws Exception {
		// given
		String constraint = "fail_prize_" + hex(event);
		jdbc.execute("alter table event_prizes add constraint " + constraint + " check(id<>X'"
				+ hex(firstPrize) + "' or name<>'실패')");
		try {
			// when / then
			modify(body(START.plusSeconds(600), "롤백", prizeJson(firstPrize, 2, "실패"), prizeJson(secondPrize, 1, "교환")))
					.andExpect(status().isInternalServerError());
			assertOriginal();
			assertOriginalPeriodAndPrizes();
			assertThat(jdbc.queryForObject("select prize_rank from event_prizes where id=?", Integer.class, bytes(firstPrize))).isEqualTo(1);
			assertThat(jobsCount()).isZero();
		} finally {
			jdbc.execute("alter table event_prizes drop check " + constraint);
		}
	}

	@Test
	@DisplayName("알림 작업 등록 실패는 수정과 기존 시작 작업 중지를 함께 롤백한다")
	void notificationFailureRollsBackModificationAndStartJobStop() throws Exception {
		// given
		UUID startJob = startJob(user("USER"));
		String constraint = "fail_change_" + hex(event);
		jdbc.execute("alter table notification_jobs add constraint " + constraint + " check(event_id<>X'"
				+ hex(event) + "' or notification_type<>'EVENT_SCHEDULE_CHANGED')");
		try {
			// when / then
			modify(body(START.plusSeconds(600), "롤백", prizeJson(firstPrize, 2, "교환"), prizeJson(secondPrize, 1, "교환")))
					.andExpect(status().isInternalServerError());
			assertOriginal();
			assertOriginalPeriodAndPrizes();
			assertThat(jdbc.queryForObject("select prize_rank from event_prizes where id=?", Integer.class, bytes(firstPrize))).isEqualTo(1);
			assertThat(jobsCount()).isZero();
			assertThat(jdbc.queryForObject("select json_unquote(json_extract(payload,'$.generationStoppedBy')) from notification_jobs where id=?",
					String.class, bytes(startJob))).isNull();
		} finally {
			jdbc.execute("alter table notification_jobs drop check " + constraint);
		}
	}

	private ResultActions modify(String body) throws Exception {
		return mvc.perform(put("/api/v1/admin/events/" + event).header("X-User-ID", admin.toString()).header("X-User-Role", "ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String body(Instant start, String title, String... prizes) {
		return """
			{"title":"%s", "description": "설명", "imageKey":"events/edited.png", "eventType":"TICKET",
			 "weightingEnabled":true, "maxTicketsPerUser":5, "membershipRule":"vip",
			 "startsAt":"%s", "endsAt":"%s", "prizes":[%s]}
			""".formatted(title, start, END, String.join(",", prizes));
	}

	private String prizeJson(UUID id, int rank, String name) {
		return """
			{"id":%s,"rank":%d,"name":"%s","description":"경품 설명","imageKey":"prizes/edited.png","winnerCount":2}
			""".formatted(id == null ? "null" : "\"" + id + "\"", rank, name);
	}

	private UUID user(String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into users(id,name,role,status,membership,created_at,updated_at) values(?,'수정 테스트',?,'ACTIVE','VIP',?,?)",
				bytes(id), role, utc(CREATED), utc(CREATED));
		fixtureUsers.add(id);
		return id;
	}

	private UUID event() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
			insert into events(id,title,description,event_type,weighting_enabled,max_tickets_per_user,membership_rule,
			starts_at,ends_at,status,created_at,updated_at) values(?,'원본','원본 설명','TICKET',true,5,'vip',?,?,'SCHEDULED',?,?)
			""", bytes(id), utc(START), utc(END), utc(CREATED), utc(CREATED));
		fixtureEvents.add(id);
		return id;
	}

	private UUID prize(UUID eventId, int rank, String name) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into event_prizes(id,event_id,prize_rank,name,winner_count,created_at,updated_at) values(?,?,?,?,1,?,?)",
				bytes(id), bytes(eventId), rank, name, utc(CREATED), utc(CREATED));
		return id;
	}

	private UUID startJob(UUID recipient) {
		UUID job = jobs.register(new NotificationJobRequest("modification-start:" + event, NotificationType.EVENT_START,
				event, null, "원래 시작 안내", "안내", "/events/" + event, START.minusSeconds(600), List.of(recipient)));
		jdbc.update("""
			insert into notifications(id,job_id,user_id,event_id,title,body,created_at,is_read,mock_delivery_status)
			values(?,?,?,?,'기존 시작 알림','이력',?,false,'SENT')
			""", bytes(UUID.randomUUID()), bytes(job), bytes(recipient), bytes(event), utc(CREATED));
		return job;
	}

	private long jobsCount() {
		return jdbc.queryForObject("select count(*) from notification_jobs where event_id=? and notification_type='EVENT_SCHEDULE_CHANGED'",
				Long.class, bytes(event));
	}

	private void assertOriginal() {
		assertThat(jdbc.queryForObject("select title from events where id=?", String.class, bytes(event))).isEqualTo("원본");
		assertThat(jdbc.queryForObject("select count(*) from event_prizes where event_id=?", Long.class, bytes(event))).isEqualTo(2);
	}

	private void assertOriginalPeriodAndPrizes() {
		assertThat(jdbc.queryForObject("select starts_at from events where id=?", LocalDateTime.class, bytes(event)))
				.isEqualTo(utc(START));
		assertThat(jdbc.queryForObject("select ends_at from events where id=?", LocalDateTime.class, bytes(event)))
				.isEqualTo(utc(END));
		assertThat(jdbc.queryForList("select name from event_prizes where event_id=? order by prize_rank", String.class, bytes(event)))
				.containsExactly("첫 경품", "둘째 경품");
	}

	private byte[] bytes(UUID id) { return UuidBinary.toBytes(id); }
	private LocalDateTime utc(Instant time) { return LocalDateTime.ofInstant(time, ZoneOffset.UTC); }
	private String hex(UUID id) { return id.toString().replace("-", ""); }
}
