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
	@DisplayName("제출은 미션의 보상 정책과 미션 ID를 그대로 기록하고 클라이언트가 정한 ID와 서버가 받은 시각을 가진다")
	void recordsMissionReferences() {
		// given
		Mission mission = saved(MissionType.QUIZ);
		UUID submissionId = UUID.randomUUID();
		// when
		MissionSubmission submission = MissionSubmission.of(submissionId, STARTS, mission, USER_ID, true,
				List.of(correctText(UUID.randomUUID())), List.of());
		// then
		assertThat(submission.getMissionId()).isEqualTo(mission.getId());
		assertThat(submission.getRewardPolicyId()).isEqualTo(mission.getRewardPolicyId());
		assertThat(submission.getUserId()).isEqualTo(USER_ID);
		assertThat(submission.isCompleted()).isTrue();
		assertThat(submission.getId()).isEqualTo(submissionId);
		assertThat(submission.getCreatedAt()).isEqualTo(STARTS);
	}

	@Test
	@DisplayName("퀴즈 완료 제출은 답변이 있고 모두 정답일 때만 만들 수 있고 오답 제출은 미완료로 남긴다")
	void completedQuizRequiresCorrectAnswers() {
		// given
		Mission quiz = saved(MissionType.QUIZ);
		QuizAnswer wrong = QuizAnswer.of(UUID.randomUUID(), null, "오답", false);
		// when
		MissionSubmission incomplete = MissionSubmission.of(UUID.randomUUID(), STARTS, quiz, USER_ID, false, List.of(wrong), List.of());
		// then
		assertThat(incomplete.isCompleted()).isFalse();
		assertInvalidAnswer(() -> MissionSubmission.of(UUID.randomUUID(), STARTS, quiz, USER_ID, true, List.of(wrong), List.of()));
		assertInvalidAnswer(() -> MissionSubmission.of(UUID.randomUUID(), STARTS, quiz, USER_ID, true,
				List.of(correctText(UUID.randomUUID()), wrong), List.of()));
		assertInvalidAnswer(() -> MissionSubmission.of(UUID.randomUUID(), STARTS, quiz, USER_ID, true, List.of(), List.of()));
	}

	@Test
	@DisplayName("생성자로 직접 만들어도 팩터리와 같은 규칙을 지켜 오답이 있는 제출을 완료로 만들 수 없다")
	void constructorRejectsCompletedSubmissionWithWrongAnswer() {
		// given
		Mission quiz = saved(MissionType.QUIZ);
		QuizAnswer wrong = QuizAnswer.of(UUID.randomUUID(), null, "오답", false);
		QuizAnswer correct = correctText(UUID.randomUUID());
		// when
		MissionSubmission incomplete = new MissionSubmission(null, quiz.getId(), quiz.getRewardPolicyId(), USER_ID, false,
				null, List.of(wrong), List.of());
		MissionSubmission completed = new MissionSubmission(null, quiz.getId(), quiz.getRewardPolicyId(), USER_ID, true,
				null, List.of(correct), List.of());
		// then
		assertThat(incomplete.isCompleted()).isFalse();
		assertThat(completed.isCompleted()).isTrue();
		assertInvalidAnswer(() -> new MissionSubmission(null, quiz.getId(), quiz.getRewardPolicyId(), USER_ID, true,
				null, List.of(wrong), List.of()));
		assertInvalidAnswer(() -> new MissionSubmission(null, quiz.getId(), quiz.getRewardPolicyId(), USER_ID, true,
				null, List.of(correct, wrong), List.of()));
	}

	@Test
	@DisplayName("생성자로 직접 만들어도 같은 문항에 두 번 답한 제출은 거절한다")
	void constructorRejectsDuplicateQuestionAnswers() {
		// given
		Mission survey = saved(MissionType.SURVEY);
		UUID questionId = UUID.randomUUID();
		// when
		// then
		assertInvalidAnswer(() -> new MissionSubmission(null, survey.getId(), survey.getRewardPolicyId(), USER_ID, true,
				null, List.of(), List.of(SurveyAnswer.of(questionId, null, "가"), SurveyAnswer.of(questionId, null, "나"))));
		assertInvalidAnswer(() -> new MissionSubmission(null, UUID.randomUUID(), UUID.randomUUID(), USER_ID, false, null,
				List.of(correctText(questionId), correctText(questionId)), List.of()));
	}

	@Test
	@DisplayName("답변도 생성자로 직접 만들 때 선택지와 입력 문자열 중 정확히 하나만 허용한다")
	void answerConstructorRequiresExactlyOneValue() {
		// given
		UUID questionId = UUID.randomUUID();
		UUID optionId = UUID.randomUUID();
		// when
		QuizAnswer byOption = new QuizAnswer(UUID.randomUUID(), questionId, optionId, null, true);
		SurveyAnswer byText = new SurveyAnswer(UUID.randomUUID(), questionId, null, "의견");
		// then
		assertThat(byOption.getSelectedOptionId()).isEqualTo(optionId);
		assertThat(byText.getAnswerText()).isEqualTo("의견");
		assertInvalidAnswer(() -> new QuizAnswer(null, questionId, null, null, false));
		assertInvalidAnswer(() -> new QuizAnswer(null, questionId, optionId, "정답", true));
		assertInvalidAnswer(() -> new QuizAnswer(null, questionId, optionId, " ", true));
		assertInvalidAnswer(() -> new SurveyAnswer(null, questionId, null, null));
		assertInvalidAnswer(() -> new SurveyAnswer(null, questionId, optionId, "의견"));
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
				.isThrownBy(() -> MissionSubmission.of(UUID.randomUUID(), STARTS, unsaved, USER_ID, false, List.of(), List.of()));
	}

	@Test
	@DisplayName("미션 유형과 다른 종류의 답변이 섞이면 답변 형식 오류다")
	void rejectsAnswersOfOtherType() {
		// given
		// when
		// then
		assertInvalidAnswer(() -> MissionSubmission.of(UUID.randomUUID(), STARTS, saved(MissionType.QUIZ), USER_ID, false, List.of(),
				List.of(SurveyAnswer.of(UUID.randomUUID(), null, "의견"))));
		assertInvalidAnswer(() -> MissionSubmission.of(UUID.randomUUID(), STARTS, saved(MissionType.SURVEY), USER_ID, true,
				List.of(correctText(UUID.randomUUID())), List.of()));
	}

	@Test
	@DisplayName("같은 문항에 두 번 답하면 답변 형식 오류다")
	void rejectsDuplicateQuestion() {
		// given
		UUID questionId = UUID.randomUUID();
		// when
		// then
		assertInvalidAnswer(() -> MissionSubmission.of(UUID.randomUUID(), STARTS, saved(MissionType.QUIZ), USER_ID, true,
				List.of(correctText(questionId), correctText(questionId)), List.of()));
		assertInvalidAnswer(() -> MissionSubmission.of(UUID.randomUUID(), STARTS, saved(MissionType.SURVEY), USER_ID, true, List.of(),
				List.of(SurveyAnswer.of(questionId, null, "가"), SurveyAnswer.of(questionId, null, "나"))));
	}

	@Test
	@DisplayName("답변은 선택지와 입력 문자열 중 정확히 하나만 가져야 한다")
	void answerHasExactlyOneValue() {
		// given
		UUID questionId = UUID.randomUUID();
		UUID optionId = UUID.randomUUID();
		// when
		// then
		assertThat(QuizAnswer.of(questionId, optionId, null, false).getSelectedOptionId()).isEqualTo(optionId);
		assertThat(SurveyAnswer.of(questionId, null, "의견").getAnswerText()).isEqualTo("의견");
		assertInvalidAnswer(() -> QuizAnswer.of(questionId, null, null, false));
		assertInvalidAnswer(() -> QuizAnswer.of(questionId, optionId, "정답", true));
		assertInvalidAnswer(() -> QuizAnswer.of(questionId, null, " ", true));
		assertInvalidAnswer(() -> SurveyAnswer.of(questionId, null, null));
		assertInvalidAnswer(() -> SurveyAnswer.of(questionId, optionId, "의견"));
	}

	@Test
	@DisplayName("선택지를 고른 답변에 빈 문자열이나 공백 입력이 함께 있어도 거절한다")
	void optionAnswerRejectsBlankText() {
		// given
		UUID questionId = UUID.randomUUID();
		UUID optionId = UUID.randomUUID();
		// when
		// then
		assertInvalidAnswer(() -> QuizAnswer.of(questionId, optionId, "", true));
		assertInvalidAnswer(() -> QuizAnswer.of(questionId, optionId, " ", true));
		assertInvalidAnswer(() -> SurveyAnswer.of(questionId, optionId, ""));
		assertInvalidAnswer(() -> SurveyAnswer.of(questionId, optionId, " "));
	}

	@Test
	@DisplayName("정답으로 완료한 문항 기록은 그 제출에 저장된 정답 답변 ID로만 만들고 사용자·문항은 저장된 값을 쓴다")
	void progressRequiresStoredCorrectAnswerOfTheSubmission() {
		// given
		Mission mission = saved(MissionType.QUIZ);
		UUID questionId = UUID.randomUUID();
		QuizAnswer savedCorrect = new QuizAnswer(UUID.randomUUID(), questionId, null, "정답", true);
		QuizAnswer savedWrong = new QuizAnswer(UUID.randomUUID(), UUID.randomUUID(), null, "오답", false);
		MissionSubmission submission = new MissionSubmission(UUID.randomUUID(), mission.getId(),
				mission.getRewardPolicyId(), USER_ID, false, STARTS, List.of(savedCorrect, savedWrong), List.of());
		// when
		QuizQuestionProgress progress = QuizQuestionProgress.of(submission, savedCorrect.getId());
		// then
		assertThat(progress.getQuestionId()).isEqualTo(questionId);
		assertThat(progress.getCorrectAnswerId()).isEqualTo(savedCorrect.getId());
		assertThat(progress.getUserId()).isEqualTo(USER_ID);
		assertInvalidAnswer(() -> QuizQuestionProgress.of(submission, savedWrong.getId()));
		assertInvalidAnswer(() -> QuizQuestionProgress.of(submission, UUID.randomUUID()));
		assertThatNullPointerException().isThrownBy(() -> QuizQuestionProgress.of(submission, null));
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
		assertThatThrownBy(() -> new MissionRewardClaim(null, mission.getRewardPolicyId(), mission.getId(), submissionId,
				USER_ID, "mission:" + mission.getId(), 2, null)).isInstanceOf(MissionException.class);
		assertThatThrownBy(() -> new MissionRewardClaim(null, mission.getRewardPolicyId(), mission.getId(), submissionId,
				USER_ID, "mission:" + mission.getId(), 0, null)).isInstanceOf(MissionException.class);
	}

	private static void assertInvalidAnswer(Runnable action) {
		assertThatThrownBy(action::run)
				.isInstanceOfSatisfying(MissionException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(MissionErrorCode.INVALID_MISSION_ANSWER));
	}
}
