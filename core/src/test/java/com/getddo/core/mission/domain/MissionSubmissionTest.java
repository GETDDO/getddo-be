package com.getddo.core.mission.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.mission.exception.MissionErrorCode;
import com.getddo.core.mission.exception.MissionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionSubmissionTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final Instant STARTS = Instant.parse("2026-10-01T00:00:00Z");

	private static Mission saved(MissionType type) {
		return new Mission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "제목", "설명", type, STARTS,
				STARTS.plusSeconds(3600), MissionStatus.ACTIVE, null, STARTS, STARTS);
	}

	private static QuizAnswer correctText(UUID questionId) {
		return QuizAnswer.of(questionId, null, "정답", true);
	}

	@Test
	@DisplayName("제출은 미션의 보상 정책과 미션 ID를 그대로 기록하고 저장 전이라 ID와 시각이 없다")
	void recordsMissionReferences() {
		// given
		Mission mission = saved(MissionType.QUIZ);
		// when
		MissionSubmission submission = MissionSubmission.of(mission, USER_ID, true,
				List.of(correctText(UUID.randomUUID())), List.of());
		// then
		assertThat(submission.getMissionId()).isEqualTo(mission.getId());
		assertThat(submission.getRewardPolicyId()).isEqualTo(mission.getRewardPolicyId());
		assertThat(submission.getUserId()).isEqualTo(USER_ID);
		assertThat(submission.isCompleted()).isTrue();
		assertThat(submission.getId()).isNull();
		assertThat(submission.getCreatedAt()).isNull();
	}

	@Test
	@DisplayName("저장되지 않은 미션에는 제출을 만들 수 없다")
	void requiresSavedMission() {
		// given
		Mission unsaved = Mission.register(UUID.randomUUID(), UUID.randomUUID(), "제목", "설명", MissionType.SURVEY,
				STARTS, STARTS.plusSeconds(1), null);
		// when
		// then
		assertThatNullPointerException()
				.isThrownBy(() -> MissionSubmission.of(unsaved, USER_ID, false, List.of(), List.of()));
	}

	@Test
	@DisplayName("미션 유형과 다른 종류의 답변이 섞이면 답변 형식 오류다")
	void rejectsAnswersOfOtherType() {
		assertInvalidAnswer(() -> MissionSubmission.of(saved(MissionType.QUIZ), USER_ID, false, List.of(),
				List.of(SurveyAnswer.of(UUID.randomUUID(), null, "의견"))));
		assertInvalidAnswer(() -> MissionSubmission.of(saved(MissionType.SURVEY), USER_ID, true,
				List.of(correctText(UUID.randomUUID())), List.of()));
	}

	@Test
	@DisplayName("같은 문항에 두 번 답하면 답변 형식 오류다")
	void rejectsDuplicateQuestion() {
		// given
		UUID questionId = UUID.randomUUID();
		// when
		// then
		assertInvalidAnswer(() -> MissionSubmission.of(saved(MissionType.QUIZ), USER_ID, true,
				List.of(correctText(questionId), correctText(questionId)), List.of()));
		assertInvalidAnswer(() -> MissionSubmission.of(saved(MissionType.SURVEY), USER_ID, true, List.of(),
				List.of(SurveyAnswer.of(questionId, null, "가"), SurveyAnswer.of(questionId, null, "나"))));
	}

	@Test
	@DisplayName("답변은 선택지와 입력 문자열 중 정확히 하나만 가져야 한다")
	void answerHasExactlyOneValue() {
		UUID questionId = UUID.randomUUID();
		UUID optionId = UUID.randomUUID();
		assertThat(QuizAnswer.of(questionId, optionId, null, false).getSelectedOptionId()).isEqualTo(optionId);
		assertThat(SurveyAnswer.of(questionId, null, "의견").getAnswerText()).isEqualTo("의견");
		assertInvalidAnswer(() -> QuizAnswer.of(questionId, null, null, false));
		assertInvalidAnswer(() -> QuizAnswer.of(questionId, optionId, "정답", true));
		assertInvalidAnswer(() -> QuizAnswer.of(questionId, null, " ", true));
		assertInvalidAnswer(() -> SurveyAnswer.of(questionId, null, null));
		assertInvalidAnswer(() -> SurveyAnswer.of(questionId, optionId, "의견"));
	}

	@Test
	@DisplayName("정답으로 완료한 문항 기록은 저장된 정답 답변을 근거로만 만든다")
	void progressRequiresSavedCorrectAnswer() {
		// given
		UUID questionId = UUID.randomUUID();
		UUID answerId = UUID.randomUUID();
		QuizAnswer savedCorrect = new QuizAnswer(answerId, questionId, null, "정답", true);
		QuizAnswer savedWrong = new QuizAnswer(answerId, questionId, null, "오답", false);
		// when
		QuizQuestionProgress progress = QuizQuestionProgress.of(USER_ID, savedCorrect);
		// then
		assertThat(progress.getQuestionId()).isEqualTo(questionId);
		assertThat(progress.getCorrectAnswerId()).isEqualTo(answerId);
		assertThat(progress.getUserId()).isEqualTo(USER_ID);
		assertInvalidAnswer(() -> QuizQuestionProgress.of(USER_ID, savedWrong));
		assertThatNullPointerException().isThrownBy(() -> QuizQuestionProgress.of(USER_ID, correctText(questionId)));
	}

	@Test
	@DisplayName("보상 청구는 완료한 저장 제출로만 만들고 응모권 1장·미션별 키를 갖는다")
	void rewardClaimFromCompletedSubmission() {
		// given
		Mission mission = saved(MissionType.SURVEY);
		UUID submissionId = UUID.randomUUID();
		MissionSubmission completed = new MissionSubmission(submissionId, mission.getId(), mission.getRewardPolicyId(),
				USER_ID, true, STARTS, List.of(), List.of());
		MissionSubmission incomplete = new MissionSubmission(submissionId, mission.getId(), mission.getRewardPolicyId(),
				USER_ID, false, STARTS, List.of(), List.of());
		// when
		MissionRewardClaim claim = MissionRewardClaim.of(completed);
		// then
		assertThat(claim.getMissionSubmissionId()).isEqualTo(submissionId);
		assertThat(claim.getMissionId()).isEqualTo(mission.getId());
		assertThat(claim.getRewardPolicyId()).isEqualTo(mission.getRewardPolicyId());
		assertThat(claim.getUserId()).isEqualTo(USER_ID);
		assertThat(claim.getTicketCount()).isEqualTo(1);
		assertThat(claim.getSourceKey()).isEqualTo("mission:" + mission.getId());
		assertThatThrownBy(() -> MissionRewardClaim.of(incomplete)).isInstanceOf(MissionException.class);
		assertThatNullPointerException().isThrownBy(() -> MissionRewardClaim.of(MissionSubmission.of(mission, USER_ID,
				true, List.of(), List.of(SurveyAnswer.of(UUID.randomUUID(), null, "의견")))));
	}

	private static void assertInvalidAnswer(Runnable action) {
		assertThatThrownBy(action::run)
				.isInstanceOfSatisfying(MissionException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(MissionErrorCode.INVALID_MISSION_ANSWER));
	}
}
