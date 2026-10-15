package com.getddo.api.mission;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 미션 제출 API(M03)를 HTTP부터 실제 MySQL까지 전 구간으로 검증한다.
 *
 * <p>서버는 실제 시계로 운영 기간과 응모권 만료를 판정한다. 테스트가 시각 경계에 걸려도 결과가 바뀌지 않도록 미션은 어제부터
 * 30일 동안 운영한다. 제출 뒤 응모권 조회(T01)로 보상이 보유에 반영됐는지도 확인한다.</p>
 */
@ApiIntegrationTest
class MissionApiIntegrationTest {

	private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000401");
	private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000402");
	private static final UUID MISSION = UUID.fromString("00000000-0000-0000-0000-000000000410");
	private static final UUID QUESTION = UUID.fromString("00000000-0000-0000-0000-000000000411");
	private static final UUID OPTION_OK = UUID.fromString("00000000-0000-0000-0000-000000000412");
	private static final UUID OPTION_NO = UUID.fromString("00000000-0000-0000-0000-000000000413");
	private static final UUID SHORT_QUESTION = UUID.fromString("00000000-0000-0000-0000-000000000414");
	private static final UUID POLICY = UUID.fromString("00000000-0000-0000-0000-000000000415");

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void seed() {
		LocalDateTime now = utc(Instant.now());
		jdbc.update("""
				insert into users (id, name, role, status, membership, updated_at, created_at)
				values (?, 'test', 'USER', 'ACTIVE', 'vip', ?, ?)
				""", bytes(USER), now, now);
		jdbc.update("""
				insert into users (id, name, role, status, membership, updated_at, created_at)
				values (?, 'other', 'USER', 'ACTIVE', 'vip', ?, ?)
				""", bytes(OTHER), now, now);
		jdbc.update("""
				insert into reward_policies (id, created_by, reward_type, reward_ticket_count, effective_from,
				  effective_until, created_at)
				values (?, ?, 'MISSION', 1, ?, ?, ?)
				""", bytes(POLICY), bytes(USER), utc(Instant.now().minus(Duration.ofDays(1))),
				utc(Instant.now().plus(Duration.ofDays(365))), now);
		jdbc.update("""
				insert into missions (id, reward_policy_id, created_by, title, description, mission_type, starts_at,
				  ends_at, status, created_at, updated_at)
				values (?, ?, ?, 'API 테스트 미션', '설명', 'QUIZ', ?, ?, 'ACTIVE', ?, ?)
				""", bytes(MISSION), bytes(POLICY), bytes(USER), utc(Instant.now().minus(Duration.ofDays(1))),
				utc(Instant.now().plus(Duration.ofDays(30))), now, now);
		jdbc.update("""
				insert into mission_quiz_questions (id, mission_id, question_type, question_text, display_order, created_at)
				values (?, ?, 'SINGLE_CHOICE', '수도는?', 1, ?)
				""", bytes(QUESTION), bytes(MISSION), now);
		jdbc.update("""
				insert into quiz_question_options (id, quiz_question_id, option_text, display_order, is_correct, created_at)
				values (?, ?, '서울', 1, true, ?), (?, ?, '부산', 2, false, ?)
				""", bytes(OPTION_OK), bytes(QUESTION), now, bytes(OPTION_NO), bytes(QUESTION), now);
		jdbc.update("""
				insert into mission_quiz_questions (id, mission_id, question_type, question_text, display_order, created_at,
				  correct_answer)
				values (?, ?, 'SHORT_ANSWER', '브랜드는?', 2, ?, '겟또')
				""", bytes(SHORT_QUESTION), bytes(MISSION), now);
	}

	@AfterEach
	void clean() {
		jdbc.update("delete h from ticket_histories h join tickets t on t.id = h.ticket_id where t.user_id = ?", bytes(OTHER));
		jdbc.update("delete from tickets where user_id = ?", bytes(OTHER));
		jdbc.update("delete from mission_reward_claims where user_id = ?", bytes(OTHER));
		jdbc.update("delete from quiz_question_progress where user_id = ?", bytes(OTHER));
		jdbc.update("delete a from quiz_answers a join mission_submissions s on s.id = a.submission_id where s.user_id = ?",
				bytes(OTHER));
		jdbc.update("delete from mission_submissions where user_id = ?", bytes(OTHER));
		jdbc.update("delete h from ticket_histories h join tickets t on t.id = h.ticket_id where t.user_id = ?", bytes(USER));
		jdbc.update("delete from tickets where user_id = ?", bytes(USER));
		jdbc.update("delete from mission_reward_claims where user_id = ?", bytes(USER));
		jdbc.update("delete from quiz_question_progress where user_id = ?", bytes(USER));
		jdbc.update("delete a from quiz_answers a join mission_submissions s on s.id = a.submission_id where s.user_id = ?",
				bytes(USER));
		jdbc.update("delete from mission_submissions where user_id = ?", bytes(USER));
		jdbc.update("delete from quiz_question_options where quiz_question_id = ?", bytes(QUESTION));
		jdbc.update("delete from mission_quiz_questions where mission_id = ?", bytes(MISSION));
		jdbc.update("delete from missions where id = ?", bytes(MISSION));
		jdbc.update("delete from reward_policies where id = ?", bytes(POLICY));
		jdbc.update("delete from users where id = ?", bytes(OTHER));
		jdbc.update("delete from users where id = ?", bytes(USER));
	}

	private static String answers(UUID option, String text) {
		return "{\"answers\":[{\"questionId\":\"" + QUESTION + "\",\"selectedOptionId\":\"" + option + "\"},"
				+ "{\"questionId\":\"" + SHORT_QUESTION + "\",\"answerText\":\"" + text + "\"}]}";
	}

	@Test
	@DisplayName("정답을 모두 맞히면 201과 응모권 보상을 반환하고 보유 응모권이 늘며 같은 키로 다시 보내면 200이고 더 늘지 않는다")
	void completesGrantsTicketAndReplays() throws Exception {
		// given
		UUID key = UUID.randomUUID();
		// when
		// then
		String created = mvc.perform(submit(key, answers(OPTION_OK, "겟또")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.submissionId").value(key.toString()))
				.andExpect(jsonPath("$.data.missionId").value(MISSION.toString()))
				.andExpect(jsonPath("$.data.isCompleted").value(true))
				.andExpect(jsonPath("$.data.reward.claimId").isString())
				.andExpect(jsonPath("$.data.reward.ticketCount").value(1))
				.andExpect(jsonPath("$.data.reward.grade").isString())
				.andExpect(jsonPath("$.data.reward.expiresAt").isString())
				.andReturn().getResponse().getContentAsString();
		String claimId = JsonPath.read(created, "$.data.reward.claimId");
		mvc.perform(get("/api/v1/tickets/me").headers(headers()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.availableCount").value(1));
		mvc.perform(submit(key, answers(OPTION_OK, "겟또")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.submissionId").value(key.toString()))
				.andExpect(jsonPath("$.data.isCompleted").value(true))
				.andExpect(jsonPath("$.data.reward.claimId").value(claimId));
		mvc.perform(get("/api/v1/tickets/me").headers(headers()))
				.andExpect(jsonPath("$.data.availableCount").value(1));
		assertThat(count("select count(*) from mission_reward_claims where user_id = ?")).isEqualTo(1);
	}

	@Test
	@DisplayName("오답이면 201이지만 isCompleted=false, reward=null이고 응모권이 늘지 않으며 같은 키에 다른 답변이면 409다")
	void wrongAnswerIsIncompleteAndKeyConflictIsRejected() throws Exception {
		// given
		UUID key = UUID.randomUUID();
		// when
		// then
		mvc.perform(submit(key, answers(OPTION_NO, "겟또")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.isCompleted").value(false))
				.andExpect(jsonPath("$.data.reward").value(nullValue()));
		mvc.perform(get("/api/v1/tickets/me").headers(headers()))
				.andExpect(jsonPath("$.data.availableCount").value(0));
		mvc.perform(submit(key, answers(OPTION_OK, "겟또")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MISSION-005"));
	}

	@Test
	@DisplayName("없는 미션은 404, 문항에 속하지 않는 선택지는 400, 본문 오류는 400이다")
	void mapsMissingMissionAndBadAnswers() throws Exception {
		// given
		UUID otherMission = UUID.randomUUID();
		// when
		// then
		mvc.perform(post("/api/v1/missions/{missionId}/submissions", otherMission).headers(headers())
						.header("Idempotency-Key", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON).content(answers(OPTION_OK, "겟또")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MISSION-003"));
		mvc.perform(submit(UUID.randomUUID(), answers(UUID.randomUUID(), "겟또")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MISSION-002"));
		mvc.perform(submit(UUID.randomUUID(), "{\"answers\":[]}")).andExpect(status().isBadRequest());
		assertThat(count("select count(*) from mission_submissions where user_id = ?")).isZero();
	}

	private long count(String sql) {
		return jdbc.queryForObject(sql, Long.class, bytes(USER));
	}

	private MockHttpServletRequestBuilder submit(UUID key, String body) {
		return submit(USER, key, body);
	}

	private MockHttpServletRequestBuilder submit(UUID user, UUID key, String body) {
		return post("/api/v1/missions/{missionId}/submissions", MISSION).headers(headers(user))
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

	private HttpHeaders headers() {
		return headers(USER);
	}

	private HttpHeaders headers(UUID user) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-User-ID", user.toString());
		headers.set("X-User-Role", "USER");
		return headers;
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
				.putLong(id.getLeastSignificantBits()).array();
	}

	@Test
	@DisplayName("목록과 상세는 시드한 미션을 보여 주고 완료 전에는 completed=false, 완료 뒤에는 true이며 정답은 응답에 없다")
	void listAndDetailShowMissionWithCompletionAndNoAnswers() throws Exception {
		// given
		String missionFilter = "$.data.items[?(@.id=='" + MISSION + "')]";
		// when
		// then
		mvc.perform(get("/api/v1/missions").param("missionType", "QUIZ").param("size", "100").headers(headers()))
				.andExpect(status().isOk())
				.andExpect(jsonPath(missionFilter + ".title").value("API 테스트 미션"))
				.andExpect(jsonPath(missionFilter + ".rewardTicketCount").value(1))
				.andExpect(jsonPath(missionFilter + ".completed").value(false));
		mvc.perform(get("/api/v1/missions/{missionId}", MISSION).headers(headers()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(MISSION.toString()))
				.andExpect(jsonPath("$.data.completed").value(false))
				.andExpect(jsonPath("$.data.questions.length()").value(2))
				.andExpect(jsonPath("$.data.questions[0].id").value(QUESTION.toString()))
				.andExpect(jsonPath("$.data.questions[0].options.length()").value(2))
				.andExpect(jsonPath("$.data.questions[1].id").value(SHORT_QUESTION.toString()))
				.andExpect(content().string(not(containsString("겟또"))))
				.andExpect(content().string(not(containsString("correct"))));
		mvc.perform(submit(UUID.randomUUID(), answers(OPTION_OK, "겟또"))).andExpect(status().isCreated());
		mvc.perform(get("/api/v1/missions").param("missionType", "QUIZ").param("size", "100").headers(headers()))
				.andExpect(jsonPath(missionFilter + ".completed").value(true));
		mvc.perform(get("/api/v1/missions/{missionId}", MISSION).headers(headers()))
				.andExpect(jsonPath("$.data.completed").value(true));
	}

	@Test
	@DisplayName("목록의 잘못된 페이지 크기는 400이고 없는 미션의 상세 조회는 404 MISSION-003이다")
	void rejectsInvalidSizeAndUnknownMission() throws Exception {
		// given
		// when
		// then
		mvc.perform(get("/api/v1/missions").param("size", "0").headers(headers()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));
		mvc.perform(get("/api/v1/missions/{missionId}", UUID.randomUUID()).headers(headers()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MISSION-003"));
	}

	@Test
	@DisplayName("내 제출 조회는 오답과 완료 제출을 보여 주되 보상은 완료 제출에만 붙고 다른 사용자의 제출은 보이지 않으며 없는 미션은 404다")
	void listsMySubmissionsWithRewardOnlyOnCompletedAndHidesOtherUsers() throws Exception {
		// given
		UUID wrongKey = UUID.randomUUID();
		UUID doneKey = UUID.randomUUID();
		UUID otherKey = UUID.randomUUID();
		mvc.perform(submit(wrongKey, answers(OPTION_NO, "겟또"))).andExpect(status().isCreated());
		String doneBody = mvc.perform(submit(doneKey, answers(OPTION_OK, "겟또"))).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String doneClaimId = JsonPath.read(doneBody, "$.data.reward.claimId");
		mvc.perform(submit(OTHER, otherKey, answers(OPTION_OK, "겟또"))).andExpect(status().isCreated());
		String done = "$.data.items[?(@.submissionId=='" + doneKey + "')]";
		String wrong = "$.data.items[?(@.submissionId=='" + wrongKey + "')]";
		// when
		// then
		mvc.perform(get("/api/v1/missions/{missionId}/submissions/me", MISSION).headers(headers()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath(done + ".isCompleted").value(true))
				.andExpect(jsonPath(done + ".reward.claimId").value(contains(doneClaimId)))
				.andExpect(jsonPath(done + ".reward.ticketCount").value(1))
				.andExpect(jsonPath(done + ".reward.grade").isNotEmpty())
				.andExpect(jsonPath(wrong + ".isCompleted").value(false))
				.andExpect(jsonPath(wrong + ".reward").value(contains(nullValue())))
				.andExpect(jsonPath("$.data.items[?(@.submissionId=='" + otherKey + "')]").isEmpty())
				.andExpect(jsonPath("$.data.items[*].answers").isEmpty())
				.andExpect(jsonPath("$.data.items[*].isCorrect").isEmpty())
				.andExpect(jsonPath("$.data.items[*].selectedOptionId").isEmpty());
		mvc.perform(get("/api/v1/missions/{missionId}/submissions/me", MISSION).headers(headers(OTHER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.items[0].submissionId").value(otherKey.toString()));
		mvc.perform(get("/api/v1/missions/{missionId}/submissions/me", MISSION).param("size", "101").headers(headers()))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/missions/{missionId}/submissions/me", UUID.randomUUID()).headers(headers()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MISSION-003"));
	}
}
