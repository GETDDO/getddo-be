package com.getddo.api.mission.controller;

import java.time.Instant;
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
import com.getddo.core.mission.domain.MissionRewardReceipt;
import com.getddo.core.mission.domain.MissionSubmissionCommand;
import com.getddo.core.mission.domain.MissionSubmissionResult;
import com.getddo.core.mission.exception.MissionErrorCode;
import com.getddo.core.mission.exception.MissionException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MissionControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final User USER = new User(USER_ID, "사용자", UserRole.USER, UserStatus.ACTIVE,
			Membership.VIP, null, null, null, null, null, null);
	private static final UUID MISSION_ID = UUID.randomUUID();
	private static final UUID SUBMISSION_ID = UUID.randomUUID();
	private static final UUID QUESTION_ID = UUID.randomUUID();
	private static final UUID OPTION_ID = UUID.randomUUID();
	private static final Instant RECEIVED_AT = Instant.parse("2026-10-13T03:00:00Z");

	private final MissionSubmissionService submissionService = mock(MissionSubmissionService.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new MissionController(submissionService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver(
						new UserService(id -> Optional.of(USER).filter(user -> user.getId().equals(id)))))
				.build();
	}

	private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
		return request.header("X-User-ID", USER_ID).header("X-User-Role", "USER").header("X-User-Membership", "vip");
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
		return new MissionSubmissionResult(SUBMISSION_ID, MISSION_ID, RECEIVED_AT, true, new MissionRewardReceipt(1,
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
		mvc.perform(submit(answers())).andExpect(jsonPath("$.success").value(false));
	}
}
