package com.getddo.api.mission.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.getddo.api.common.context.CurrentUserArgumentResolver;
import com.getddo.api.common.exception.GlobalExceptionHandler;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.mission.domain.Mission;
import com.getddo.core.mission.domain.MissionDetail;
import com.getddo.core.mission.domain.MissionDetailView;
import com.getddo.core.mission.domain.MissionRewardReceipt;
import com.getddo.core.mission.domain.MissionStatus;
import com.getddo.core.mission.domain.MissionSummary;
import com.getddo.core.mission.domain.MissionType;
import com.getddo.core.mission.domain.QuizOption;
import com.getddo.core.mission.domain.QuizQuestion;
import com.getddo.core.mission.domain.QuizQuestionType;
import com.getddo.core.mission.domain.SurveyOption;
import com.getddo.core.mission.domain.SurveyQuestion;
import com.getddo.core.mission.domain.SurveyQuestionType;
import com.getddo.core.mission.domain.MissionSubmissionCommand;
import com.getddo.core.mission.domain.MissionSubmissionResult;
import com.getddo.core.mission.exception.MissionErrorCode;
import com.getddo.core.mission.exception.MissionException;
import com.getddo.core.mission.service.MissionQueryService;
import com.getddo.core.mission.service.MissionSubmissionQueryService;
import com.getddo.core.mission.service.MissionSubmissionService;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.user.domain.Membership;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MissionControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final User USER = new User(USER_ID, "사용자", UserRole.USER, UserStatus.ACTIVE,
			Membership.VIP, null, null, null, null, null, null);
	private static final UUID MISSION_ID = UUID.randomUUID();
	private static final UUID SUBMISSION_ID = UUID.randomUUID();
	private static final UUID CLAIM_ID = UUID.randomUUID();
	private static final UUID QUESTION_ID = UUID.randomUUID();
	private static final UUID OPTION_ID = UUID.randomUUID();
	private static final Instant RECEIVED_AT = Instant.parse("2026-10-13T03:00:00Z");

	private static final Instant NOW = Instant.parse("2026-10-13T03:00:00Z");
	private static final String SECRET_ANSWER = "비밀정답문자열";

	private final MissionQueryService queryService = mock(MissionQueryService.class);
	private final MissionSubmissionQueryService submissionQueryService = mock(MissionSubmissionQueryService.class);
	private final MissionSubmissionService submissionService = mock(MissionSubmissionService.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new MissionController(queryService, submissionQueryService, submissionService,
						new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC))))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver(
						new UserService(id -> Optional.of(USER).filter(user -> user.getId().equals(id)))))
				.build();
	}

	private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
		return request.header("X-User-ID", USER_ID).header("X-User-Role", "USER");
	}

	private MockHttpServletRequestBuilder submit(String body) {
		return asUser(post("/api/v1/missions/{missionId}/submissions", MISSION_ID))
				.header("Idempotency-Key", SUBMISSION_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

	private static String answers() {
		return "{\"answers\":[{\"questionId\":\"" + QUESTION_ID + "\",\"selectedOptionId\":\"" + OPTION_ID + "\"},"
				+ "{\"questionId\":\"" + UUID.randomUUID() + "\",\"answerText\":\"겟또\"}]}";
	}

	private static MissionSubmissionResult completed(boolean created) {
		return new MissionSubmissionResult(SUBMISSION_ID, MISSION_ID, RECEIVED_AT, true, new MissionRewardReceipt(CLAIM_ID, 1,
				TicketGrade.SILVER, Instant.parse("2026-10-13T03:00:00Z"), Instant.parse("2026-10-31T15:00:00Z")), created);
	}

	@Test
	@DisplayName("새로 완료한 제출은 201과 보상 영수증을 반환하고 요청을 서비스 입력으로 옮긴다")
	void returnsCreatedWithRewardAndPassesRequestToService() throws Exception {
		// given
		when(submissionService.submit(any())).thenReturn(completed(true));
		// when
		// then
		mvc.perform(submit(answers()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.submissionId").value(SUBMISSION_ID.toString()))
				.andExpect(jsonPath("$.data.missionId").value(MISSION_ID.toString()))
				.andExpect(jsonPath("$.data.receivedAt").value("2026-10-13T03:00:00Z"))
				.andExpect(jsonPath("$.data.createdAt").value("2026-10-13T03:00:00Z"))
				.andExpect(jsonPath("$.data.isCompleted").value(true))
				.andExpect(jsonPath("$.data.reward.claimId").value(CLAIM_ID.toString()))
				.andExpect(jsonPath("$.data.reward.ticketCount").value(1))
				.andExpect(jsonPath("$.data.reward.grade").value("SILVER"))
				.andExpect(jsonPath("$.data.reward.grantedAt").value("2026-10-13T03:00:00Z"))
				.andExpect(jsonPath("$.data.reward.expiresAt").value("2026-10-31T15:00:00Z"));
		ArgumentCaptor<MissionSubmissionCommand> command = ArgumentCaptor.forClass(MissionSubmissionCommand.class);
		verify(submissionService).submit(command.capture());
		assertThat(command.getValue().getUserId()).isEqualTo(USER_ID);
		assertThat(command.getValue().getMissionId()).isEqualTo(MISSION_ID);
		assertThat(command.getValue().getSubmissionId()).isEqualTo(SUBMISSION_ID);
		assertThat(command.getValue().getAnswers()).hasSize(2);
		assertThat(command.getValue().getAnswers().get(0).getQuestionId()).isEqualTo(QUESTION_ID);
		assertThat(command.getValue().getAnswers().get(0).getSelectedOptionId()).isEqualTo(OPTION_ID);
		assertThat(command.getValue().getAnswers().get(1).getAnswerText()).isEqualTo("겟또");
	}

	@Test
	@DisplayName("같은 키의 재요청이나 이미 완료한 미션의 재제출은 200과 기존 결과를 반환한다")
	void returnsOkForReplay() throws Exception {
		// given
		when(submissionService.submit(any())).thenReturn(completed(false));
		// when
		// then
		mvc.perform(submit(answers()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.submissionId").value(SUBMISSION_ID.toString()))
				.andExpect(jsonPath("$.data.isCompleted").value(true));
	}

	@Test
	@DisplayName("정답을 맞히지 못한 유효한 제출은 정상 결과로 isCompleted=false, reward=null이다")
	void returnsIncompleteWithoutReward() throws Exception {
		// given
		when(submissionService.submit(any())).thenReturn(
				new MissionSubmissionResult(SUBMISSION_ID, MISSION_ID, RECEIVED_AT, false, null, true));
		// when
		// then
		mvc.perform(submit(answers()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.isCompleted").value(false))
				.andExpect(jsonPath("$.data.reward").value(nullValue()));
	}

	@Test
	@DisplayName("Idempotency-Key가 없거나 UUID가 아니면 서비스를 부르지 않고 400이다")
	void rejectsMissingOrMalformedKey() throws Exception {
		// given
		// when
		// then
		mvc.perform(asUser(post("/api/v1/missions/{missionId}/submissions", MISSION_ID))
						.contentType(MediaType.APPLICATION_JSON).content(answers()))
				.andExpect(status().isBadRequest());
		mvc.perform(asUser(post("/api/v1/missions/{missionId}/submissions", MISSION_ID))
						.header("Idempotency-Key", "not-a-uuid")
						.contentType(MediaType.APPLICATION_JSON).content(answers()))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(submissionService);
	}

	@Test
	@DisplayName("answers가 없거나 비었거나 문항 ID가 없는 답변이 있으면 400이다")
	void rejectsInvalidBody() throws Exception {
		// given
		// when
		// then
		mvc.perform(submit("{}")).andExpect(status().isBadRequest());
		mvc.perform(submit("{\"answers\":[]}")).andExpect(status().isBadRequest());
		mvc.perform(submit("{\"answers\":[{\"answerText\":\"겟또\"}]}")).andExpect(status().isBadRequest());
		mvc.perform(submit("{\"answers\":[null]}")).andExpect(status().isBadRequest());
		verifyNoInteractions(submissionService);
	}

	@Test
	@DisplayName("업무 오류는 코드와 상태로 변환한다: 미션 없음 404, 기간 밖·키 충돌 409, 답변 형식 400, 응모권 지급 오류는 그대로")
	void mapsErrors() throws Exception {
		// given
		// when
		// then
		doThrow(new MissionException(MissionErrorCode.MISSION_NOT_FOUND)).when(submissionService).submit(any());
		mvc.perform(submit(answers())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.code").value("MISSION-003"));
		doThrow(new MissionException(MissionErrorCode.MISSION_NOT_OPEN)).when(submissionService).submit(any());
		mvc.perform(submit(answers())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MISSION-004"));
		doThrow(new MissionException(MissionErrorCode.IDEMPOTENCY_CONFLICT)).when(submissionService).submit(any());
		mvc.perform(submit(answers())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MISSION-005"));
		doThrow(new MissionException(MissionErrorCode.INVALID_MISSION_ANSWER)).when(submissionService).submit(any());
		mvc.perform(submit(answers())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSION-002"));
		doThrow(new TicketException(TicketErrorCode.TICKET_GRANT_SOURCE_NOT_FOUND)).when(submissionService).submit(any());
		mvc.perform(submit(answers()))
				.andExpect(status().is(TicketErrorCode.TICKET_GRANT_SOURCE_NOT_FOUND.getStatus()))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value(TicketErrorCode.TICKET_GRANT_SOURCE_NOT_FOUND.getCode()));
	}

	private static Mission mission(UUID id, MissionType type, String title) {
		return new Mission(id, UUID.randomUUID(), UUID.randomUUID(), title, "설명입니다", type,
				Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"), MissionStatus.ACTIVE,
				"mission/key.png", Instant.parse("2026-09-30T00:00:00Z"), Instant.parse("2026-09-30T00:00:00Z"));
	}

	private static MissionDetail quizDetail() {
		UUID correct = UUID.randomUUID();
		QuizQuestion single = new QuizQuestion(UUID.randomUUID(), QuizQuestionType.SINGLE_CHOICE, "고르세요", 1, null,
				List.of(new QuizOption(correct, "정답 선택지", 1, true), new QuizOption(UUID.randomUUID(), "오답", 2, false)));
		QuizQuestion shortAnswer = new QuizQuestion(UUID.randomUUID(), QuizQuestionType.SHORT_ANSWER, "단어는?", 2,
				SECRET_ANSWER, List.of());
		return new MissionDetail(mission(MISSION_ID, MissionType.QUIZ, "퀴즈"), List.of(single, shortAnswer), List.of());
	}

	private static MissionDetail surveyDetail() {
		SurveyQuestion free = new SurveyQuestion(UUID.randomUUID(), SurveyQuestionType.FREE_TEXT, "의견은?", false, 2,
				List.of());
		SurveyQuestion choice = new SurveyQuestion(UUID.randomUUID(), SurveyQuestionType.SINGLE_CHOICE, "만족도는?",
				true, 1, List.of(new SurveyOption(UUID.randomUUID(), "좋음", 1), new SurveyOption(UUID.randomUUID(), "보통", 2)));
		return new MissionDetail(mission(MISSION_ID, MissionType.SURVEY, "설문"), List.of(), List.of(free, choice));
	}

	@Test
	@DisplayName("목록은 미션 요약과 완료 표시, 고정 보상 장수, 서버 시각을 봉투에 담고 조건을 서비스에 전달한다")
	void listsMissionsWithCompletionAndPaging() throws Exception {
		// given
		UUID done = UUID.randomUUID();
		UUID open = UUID.randomUUID();
		when(queryService.findMissions(eq(USER_ID), eq(MissionType.QUIZ), any())).thenReturn(new PageResult<>(
				List.of(new MissionSummary(mission(done, MissionType.QUIZ, "끝낸 미션"), true),
						new MissionSummary(mission(open, MissionType.QUIZ, "새 미션"), false)), 2, 5, 12));
		// when
		// then
		mvc.perform(asUser(get("/api/v1/missions")).param("page", "2").param("size", "5").param("missionType", "QUIZ"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.page").value(2))
				.andExpect(jsonPath("$.data.size").value(5))
				.andExpect(jsonPath("$.data.totalElements").value(12))
				.andExpect(jsonPath("$.data.items[0].id").value(done.toString()))
				.andExpect(jsonPath("$.data.items[0].title").value("끝낸 미션"))
				.andExpect(jsonPath("$.data.items[0].missionType").value("QUIZ"))
				.andExpect(jsonPath("$.data.items[0].startsAt").value("2026-10-01T00:00:00Z"))
				.andExpect(jsonPath("$.data.items[0].endsAt").value("2026-11-01T00:00:00Z"))
				.andExpect(jsonPath("$.data.items[0].rewardTicketCount").value(1))
				.andExpect(jsonPath("$.data.items[0].completed").value(true))
				.andExpect(jsonPath("$.data.items[0].imageUrl").value(nullValue()))
				.andExpect(jsonPath("$.data.items[0].serverTime").value("2026-10-13T03:00:00Z"))
				.andExpect(jsonPath("$.data.items[1].completed").value(false))
				.andExpect(content().string(not(containsString("description"))));
		verify(queryService).findMissions(USER_ID, MissionType.QUIZ, new PageQuery(2, 5));
	}

	@Test
	@DisplayName("목록의 페이지 기본값은 1페이지 20건이고 유형 조건이 없으면 전체를 조회한다")
	void listUsesDefaults() throws Exception {
		// given
		when(queryService.findMissions(any(), any(), any())).thenReturn(new PageResult<>(List.of(), 1, 20, 0));
		// when
		// then
		mvc.perform(asUser(get("/api/v1/missions"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isEmpty());
		verify(queryService).findMissions(USER_ID, null, new PageQuery(1, 20));
	}

	@Test
	@DisplayName("목록의 페이지 번호나 크기가 범위를 벗어나거나 유형이 알 수 없는 값이면 400이고 조회하지 않는다")
	void rejectsInvalidListParameters() throws Exception {
		// given
		// when
		// then
		mvc.perform(asUser(get("/api/v1/missions")).param("page", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-002"));
		mvc.perform(asUser(get("/api/v1/missions")).param("size", "0")).andExpect(status().isBadRequest());
		mvc.perform(asUser(get("/api/v1/missions")).param("size", "101")).andExpect(status().isBadRequest());
		mvc.perform(asUser(get("/api/v1/missions")).param("missionType", "GAME"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
		verifyNoInteractions(queryService);
	}

	@Test
	@DisplayName("설문 상세는 설명과 문항을 표시 순서대로 담고 선택 문항의 필수 여부와 선택지를 옮긴다")
	void returnsSurveyDetailInDisplayOrder() throws Exception {
		// given
		when(queryService.findMission(USER_ID, MISSION_ID)).thenReturn(new MissionDetailView(surveyDetail(), false));
		// when
		// then
		mvc.perform(asUser(get("/api/v1/missions/{missionId}", MISSION_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(MISSION_ID.toString()))
				.andExpect(jsonPath("$.data.description").value("설명입니다"))
				.andExpect(jsonPath("$.data.completed").value(false))
				.andExpect(jsonPath("$.data.rewardTicketCount").value(1))
				.andExpect(jsonPath("$.data.serverTime").value("2026-10-13T03:00:00Z"))
				.andExpect(jsonPath("$.data.questions.length()").value(2))
				.andExpect(jsonPath("$.data.questions[0].displayOrder").value(1))
				.andExpect(jsonPath("$.data.questions[0].questionType").value("SINGLE_CHOICE"))
				.andExpect(jsonPath("$.data.questions[0].required").value(true))
				.andExpect(jsonPath("$.data.questions[0].options.length()").value(2))
				.andExpect(jsonPath("$.data.questions[0].options[0].optionText").value("좋음"))
				.andExpect(jsonPath("$.data.questions[0].options[1].displayOrder").value(2))
				.andExpect(jsonPath("$.data.questions[1].questionType").value("FREE_TEXT"))
				.andExpect(jsonPath("$.data.questions[1].required").value(false))
				.andExpect(jsonPath("$.data.questions[1].options").isEmpty());
	}

	@Test
	@DisplayName("퀴즈 상세는 모든 문항을 필수로 표시하고 정답 문자열과 선택지의 정답 여부를 응답에 담지 않는다")
	void neverExposesQuizAnswers() throws Exception {
		// given
		when(queryService.findMission(USER_ID, MISSION_ID)).thenReturn(new MissionDetailView(quizDetail(), true));
		// when
		// then
		mvc.perform(asUser(get("/api/v1/missions/{missionId}", MISSION_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.completed").value(true))
				.andExpect(jsonPath("$.data.questions[0].required").value(true))
				.andExpect(jsonPath("$.data.questions[1].required").value(true))
				.andExpect(jsonPath("$.data.questions[1].questionType").value("SHORT_ANSWER"))
				.andExpect(jsonPath("$.data.questions[0].options[0].optionText").value("정답 선택지"))
				.andExpect(content().string(not(containsString(SECRET_ANSWER))))
				.andExpect(content().string(not(containsString("correct"))))
				.andExpect(content().string(not(containsString("isCorrect"))))
				.andExpect(jsonPath("$.data.questions[0].options[0].correct").doesNotExist())
				.andExpect(jsonPath("$.data.questions[1].correctAnswer").doesNotExist());
	}

	@Test
	@DisplayName("없는 미션의 상세 조회는 404 MISSION-003이다")
	void detailOfMissingMissionIsNotFound() throws Exception {
		// given
		when(queryService.findMission(USER_ID, MISSION_ID))
				.thenThrow(new MissionException(MissionErrorCode.MISSION_NOT_FOUND));
		// when
		// then
		mvc.perform(asUser(get("/api/v1/missions/{missionId}", MISSION_ID)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("MISSION-003"));
	}

	@Test
	@DisplayName("멤버십 헤더 없이도 미션 API를 호출할 수 있고, 헤더를 보내면 DB 멤버십과 달라 409 USER-006이다")
	void membershipHeaderIsOptional() throws Exception {
		// given
		when(queryService.findMissions(any(), any(), any())).thenReturn(new PageResult<>(List.of(), 1, 20, 0));
		// when
		// then
		mvc.perform(asUser(get("/api/v1/missions")).header("X-User-Membership", "vip")).andExpect(status().isOk());
		mvc.perform(asUser(get("/api/v1/missions")).header("X-User-Membership", "vvip"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("USER-006"));
		verify(queryService).findMissions(USER_ID, null, new PageQuery(1, 20));
	}

	@Test
	@DisplayName("내 제출 조회는 오답은 보상 없이, 완료한 제출은 보상과 함께 최신순으로 봉투에 담고 조건을 서비스에 전달한다")
	void listsMySubmissionsWithRewardOnlyForCompleted() throws Exception {
		// given
		UUID wrongId = UUID.randomUUID();
		when(submissionQueryService.findMySubmissions(eq(USER_ID), eq(MISSION_ID), any())).thenReturn(new PageResult<>(
				List.of(completed(false), new MissionSubmissionResult(wrongId, MISSION_ID, RECEIVED_AT.minusSeconds(60),
						false, null, false)), 2, 5, 7));
		// when
		// then
		mvc.perform(asUser(get("/api/v1/missions/{missionId}/submissions/me", MISSION_ID))
						.param("page", "2").param("size", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.page").value(2))
				.andExpect(jsonPath("$.data.size").value(5))
				.andExpect(jsonPath("$.data.totalElements").value(7))
				.andExpect(jsonPath("$.data.items[0].submissionId").value(SUBMISSION_ID.toString()))
				.andExpect(jsonPath("$.data.items[0].missionId").value(MISSION_ID.toString()))
				.andExpect(jsonPath("$.data.items[0].isCompleted").value(true))
				.andExpect(jsonPath("$.data.items[0].receivedAt").value("2026-10-13T03:00:00Z"))
				.andExpect(jsonPath("$.data.items[0].createdAt").value("2026-10-13T03:00:00Z"))
				.andExpect(jsonPath("$.data.items[0].reward.claimId").value(CLAIM_ID.toString()))
				.andExpect(jsonPath("$.data.items[0].reward.ticketCount").value(1))
				.andExpect(jsonPath("$.data.items[0].reward.grade").value("SILVER"))
				.andExpect(jsonPath("$.data.items[1].submissionId").value(wrongId.toString()))
				.andExpect(jsonPath("$.data.items[1].isCompleted").value(false))
				.andExpect(jsonPath("$.data.items[1].reward").value(nullValue()))
				.andExpect(jsonPath("$.data.items[0].answers").doesNotExist())
				.andExpect(jsonPath("$.data.items[0].isCorrect").doesNotExist())
				.andExpect(jsonPath("$.data.items[1].selectedOptionId").doesNotExist());
		verify(submissionQueryService).findMySubmissions(USER_ID, MISSION_ID, new PageQuery(2, 5));
	}

	@Test
	@DisplayName("내 제출 조회의 페이지 기본값은 1페이지 20건이고 크기 1·100은 허용하며 0·101과 페이지 0은 400이고 조회하지 않는다")
	void mySubmissionsUsesDefaultsAndRejectsInvalidParameters() throws Exception {
		// given
		when(submissionQueryService.findMySubmissions(any(), any(), any()))
				.thenReturn(new PageResult<>(List.of(), 1, 20, 0));
		// when
		// then
		mvc.perform(asUser(get("/api/v1/missions/{missionId}/submissions/me", MISSION_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
		verify(submissionQueryService).findMySubmissions(USER_ID, MISSION_ID, new PageQuery(1, 20));
		mvc.perform(asUser(get("/api/v1/missions/{missionId}/submissions/me", MISSION_ID))
						.param("page", "1").param("size", "100"))
				.andExpect(status().isOk());
		verify(submissionQueryService).findMySubmissions(USER_ID, MISSION_ID, new PageQuery(1, 100));
		mvc.perform(asUser(get("/api/v1/missions/{missionId}/submissions/me", MISSION_ID)).param("size", "1"))
				.andExpect(status().isOk());
		verify(submissionQueryService).findMySubmissions(USER_ID, MISSION_ID, new PageQuery(1, 1));
		mvc.perform(asUser(get("/api/v1/missions/{missionId}/submissions/me", MISSION_ID)).param("page", "0"))
				.andExpect(status().isBadRequest());
		mvc.perform(asUser(get("/api/v1/missions/{missionId}/submissions/me", MISSION_ID)).param("size", "0"))
				.andExpect(status().isBadRequest());
		mvc.perform(asUser(get("/api/v1/missions/{missionId}/submissions/me", MISSION_ID)).param("size", "101"))
				.andExpect(status().isBadRequest());
		verify(submissionQueryService, times(3)).findMySubmissions(any(), any(), any());
	}

	@Test
	@DisplayName("없는 미션의 내 제출 조회는 404 MISSION-003이다")
	void mySubmissionsOfMissingMissionIsNotFound() throws Exception {
		// given
		when(submissionQueryService.findMySubmissions(any(), any(), any()))
				.thenThrow(new MissionException(MissionErrorCode.MISSION_NOT_FOUND));
		// when
		// then
		mvc.perform(asUser(get("/api/v1/missions/{missionId}/submissions/me", MISSION_ID)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MISSION-003"));
	}
}
