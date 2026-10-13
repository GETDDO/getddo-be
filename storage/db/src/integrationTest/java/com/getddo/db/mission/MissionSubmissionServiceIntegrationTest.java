package com.getddo.db.mission;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.mission.domain.MissionAnswerInput;
import com.getddo.core.mission.domain.MissionDetail;
import com.getddo.core.mission.domain.MissionSubmissionCommand;
import com.getddo.core.mission.domain.MissionSubmissionResult;
import com.getddo.core.mission.domain.QuizOption;
import com.getddo.core.mission.domain.QuizQuestion;
import com.getddo.core.mission.domain.SurveyQuestion;
import com.getddo.core.mission.exception.MissionErrorCode;
import com.getddo.core.mission.exception.MissionException;
import com.getddo.core.mission.service.MissionSubmissionService;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 미션 제출을 채점하고 보상 청구·응모권 지급까지 이어지는 흐름을 실제 MySQL로 검증한다. */
class MissionSubmissionServiceIntegrationTest extends MissionIntegrationTestSupport {

	@Autowired
	private MissionSubmissionService submissionService;

	private MissionSubmissionResult submit(UUID user, UUID missionId, UUID submissionId, MissionAnswerInput... answers) {
		return submissionService.submit(new MissionSubmissionCommand(user, missionId, submissionId, List.of(answers)));
	}

	private static MissionAnswerInput option(QuizQuestion question, int optionIndex) {
		QuizOption option = question.getOptions().get(optionIndex);
		return new MissionAnswerInput(question.getId(), option.getId(), null);
	}

	private static MissionAnswerInput text(QuizQuestion question, String answerText) {
		return new MissionAnswerInput(question.getId(), null, answerText);
	}

	/** 단일 선택(정답: 첫 선택지)·단답("겟또")·OX(정답: 첫 선택지)를 모두 맞히는 답변. */
	private static MissionAnswerInput[] allCorrect(MissionDetail quiz) {
		List<QuizQuestion> q = quiz.getQuizQuestions();
		return new MissionAnswerInput[] {option(q.get(0), 0), text(q.get(1), "겟또"), option(q.get(2), 0)};
	}

	private long tickets(UUID user) {
		return count("select count(*) from tickets where user_id = ?", bytes(user));
	}

	private long claims(UUID user) {
		return count("select count(*) from mission_reward_claims where user_id = ?", bytes(user));
	}

	private long submissions(UUID user) {
		return count("select count(*) from mission_submissions where user_id = ?", bytes(user));
	}

	private long progress(UUID user) {
		return count("select count(*) from quiz_question_progress where user_id = ?", bytes(user));
	}

	private static void assertMissionError(Throwable failure, MissionErrorCode expected) {
		assertThat(failure).isInstanceOfSatisfying(MissionException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(expected));
	}

	@Test
	@DisplayName("퀴즈의 모든 문항에 정답이면 완료하고 보상 청구와 응모권 지급, 문항별 정답 진행을 한 번에 확정한다")
	void completesQuizAndGrantsTicket() {
		// given
		MissionDetail quiz = saveQuizMission("퀴즈 미션");
		UUID submissionId = UUID.randomUUID();
		// when
		MissionSubmissionResult result = submit(userId, quiz.getMission().getId(), submissionId, allCorrect(quiz));
		// then
		assertThat(result.isCompleted()).isTrue();
		assertThat(result.isCreated()).isTrue();
		assertThat(result.getSubmissionId()).isEqualTo(submissionId);
		assertThat(result.getReceivedAt()).isEqualTo(clock.instant());
		assertThat(result.getReward().getTicketCount()).isEqualTo(1);
		assertThat(result.getReward().getGrade()).isNotNull();
		assertThat(result.getReward().getGrantedAt()).isEqualTo(clock.instant());
		assertThat(submissions(userId)).isEqualTo(1);
		assertThat(progress(userId)).isEqualTo(3);
		assertThat(claims(userId)).isEqualTo(1);
		assertThat(tickets(userId)).isEqualTo(1);
		assertThat(count("""
				select count(*) from tickets t join mission_reward_claims c on c.id = t.mission_reward_claim_id
				where t.user_id = ? and c.mission_submission_id = ?
				""", bytes(userId), bytes(submissionId))).isEqualTo(1);
		assertThat(count("""
				select count(*) from ticket_histories h join tickets t on t.id = h.ticket_id
				where t.user_id = ? and h.operation_type = 'GRANT' and h.reason = '퀴즈 미션'
				""", bytes(userId))).isEqualTo(1);
	}

	@Test
	@DisplayName("오답이면 미완료로 남기고 맞힌 문항을 누적해 다음 제출에서 남은 문항만 맞히면 완료한다")
	void accumulatesCorrectQuestionsAcrossSubmissions() {
		// given
		MissionDetail quiz = saveQuizMission("퀴즈 미션");
		UUID missionId = quiz.getMission().getId();
		List<QuizQuestion> q = quiz.getQuizQuestions();
		// when
		MissionSubmissionResult first = submit(userId, missionId, UUID.randomUUID(), option(q.get(0), 1), text(q.get(1), "겟또"));
		// then
		assertThat(first.isCompleted()).isFalse();
		assertThat(first.getReward()).isNull();
		assertThat(progress(userId)).isEqualTo(1);
		assertThat(claims(userId)).isZero();
		assertThat(tickets(userId)).isZero();
		// when: 이미 맞힌 단답에 오답을 다시 보내도 무시하고 남은 두 문항을 맞힌다
		MissionSubmissionResult second = submit(userId, missionId, UUID.randomUUID(), text(q.get(1), "틀림"),
				option(q.get(0), 0), option(q.get(2), 0));
		// then
		assertThat(second.isCompleted()).isTrue();
		assertThat(second.getReward()).isNotNull();
		assertThat(progress(userId)).isEqualTo(3);
		assertThat(claims(userId)).isEqualTo(1);
		assertThat(tickets(userId)).isEqualTo(1);
		assertThat(submissions(userId)).isEqualTo(2);
		assertThat(count("select count(*) from quiz_answers where submission_id = ?", bytes(second.getSubmissionId())))
				.isEqualTo(2);
	}

	@Test
	@DisplayName("같은 제출 ID로 같은 요청을 다시 보내면 추가 지급 없이 기존 결과를 돌려주고 다른 요청이면 409 충돌이다")
	void replaysSameSubmissionIdAndRejectsDifferentBody() {
		// given
		MissionDetail quiz = saveQuizMission("퀴즈 미션");
		UUID missionId = quiz.getMission().getId();
		UUID submissionId = UUID.randomUUID();
		MissionAnswerInput[] answers = allCorrect(quiz);
		MissionSubmissionResult first = submit(userId, missionId, submissionId, answers);
		clock.set(clock.instant().plusSeconds(60));
		// when
		MissionSubmissionResult again = submit(userId, missionId, submissionId, answers);
		// then
		assertThat(again.isCreated()).isFalse();
		assertThat(again.isCompleted()).isTrue();
		assertThat(again.getSubmissionId()).isEqualTo(first.getSubmissionId());
		assertThat(again.getReceivedAt()).isEqualTo(first.getReceivedAt());
		assertThat(again.getReward().getGrantedAt()).isEqualTo(first.getReward().getGrantedAt());
		assertThat(again.getReward().getExpiresAt()).isEqualTo(first.getReward().getExpiresAt());
		assertThat(tickets(userId)).isEqualTo(1);
		assertThat(submissions(userId)).isEqualTo(1);
		List<QuizQuestion> q = quiz.getQuizQuestions();
		assertThatThrownBy(() -> submit(userId, missionId, submissionId, option(q.get(0), 1), text(q.get(1), "겟또"),
				option(q.get(2), 0))).satisfies(e -> assertMissionError(e, MissionErrorCode.IDEMPOTENCY_CONFLICT));
		assertThatThrownBy(() -> submit(seeds.user(), missionId, submissionId, answers))
				.satisfies(e -> assertMissionError(e, MissionErrorCode.IDEMPOTENCY_CONFLICT));
		assertThatThrownBy(() -> submit(userId, saveQuizMission("다른 미션").getMission().getId(), submissionId, answers))
				.satisfies(e -> assertMissionError(e, MissionErrorCode.IDEMPOTENCY_CONFLICT));
		assertThat(tickets(userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("이미 완료한 미션을 새 제출 ID로 다시 제출하면 기존 완료 결과를 돌려주고 마감 뒤에도 같다")
	void returnsExistingCompletionOnResubmission() {
		// given
		MissionDetail quiz = saveQuizMission("퀴즈 미션");
		UUID missionId = quiz.getMission().getId();
		MissionSubmissionResult completed = submit(userId, missionId, UUID.randomUUID(), allCorrect(quiz));
		clock.set(CLOSES.plusSeconds(1));
		// when
		MissionSubmissionResult again = submit(userId, missionId, UUID.randomUUID(), allCorrect(quiz));
		// then
		assertThat(again.isCreated()).isFalse();
		assertThat(again.isCompleted()).isTrue();
		assertThat(again.getSubmissionId()).isEqualTo(completed.getSubmissionId());
		assertThat(again.getReward().getGrantedAt()).isEqualTo(completed.getReward().getGrantedAt());
		assertThat(tickets(userId)).isEqualTo(1);
		assertThat(submissions(userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("설문은 필수 문항을 모두 채우면 완료하고 필수 문항이 비면 400으로 거절하며 아무것도 저장하지 않는다")
	void surveyRequiresEveryRequiredQuestion() {
		// given
		MissionDetail survey = saveSurveyMission("설문 미션");
		UUID missionId = survey.getMission().getId();
		SurveyQuestion required = survey.getSurveyQuestions().get(0);
		SurveyQuestion optional = survey.getSurveyQuestions().get(1);
		MissionAnswerInput onlyOptional = new MissionAnswerInput(optional.getId(), null, "의견만");
		MissionAnswerInput requiredAnswer = new MissionAnswerInput(required.getId(), required.getOptions().get(0).getId(), null);
		// when
		// then
		assertThatThrownBy(() -> submit(userId, missionId, UUID.randomUUID(), onlyOptional))
				.satisfies(e -> assertMissionError(e, MissionErrorCode.INVALID_MISSION_ANSWER));
		assertThat(submissions(userId)).isZero();
		MissionSubmissionResult result = submit(userId, missionId, UUID.randomUUID(), requiredAnswer,
				new MissionAnswerInput(optional.getId(), null, "좋아요"));
		assertThat(result.isCompleted()).isTrue();
		assertThat(result.getReward().getTicketCount()).isEqualTo(1);
		assertThat(tickets(userId)).isEqualTo(1);
		assertThat(count("select count(*) from survey_answers where submission_id = ?", bytes(result.getSubmissionId())))
				.isEqualTo(2);
		assertThat(progress(userId)).isZero();
	}

	@Test
	@DisplayName("제출은 시작 시각 정각부터 종료 시각 직전까지 받고 종료 정각부터는 409로 거절한다")
	void acceptsOnlyInsideTheOperatingWindow() {
		// given
		MissionDetail survey = saveSurveyMission("설문 미션");
		UUID missionId = survey.getMission().getId();
		SurveyQuestion required = survey.getSurveyQuestions().get(0);
		MissionAnswerInput answer = new MissionAnswerInput(required.getId(), required.getOptions().get(0).getId(), null);
		// when
		// then
		clock.set(OPENS.minusNanos(1000));
		assertThatThrownBy(() -> submit(userId, missionId, UUID.randomUUID(), answer))
				.satisfies(e -> assertMissionError(e, MissionErrorCode.MISSION_NOT_OPEN));
		clock.set(CLOSES);
		assertThatThrownBy(() -> submit(userId, missionId, UUID.randomUUID(), answer))
				.satisfies(e -> assertMissionError(e, MissionErrorCode.MISSION_NOT_OPEN));
		assertThat(submissions(userId)).isZero();
		clock.set(CLOSES.minusNanos(1000));
		assertThat(submit(userId, missionId, UUID.randomUUID(), answer).isCompleted()).isTrue();
		assertThat(tickets(userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("없는 미션은 404로 거절한다")
	void rejectsMissingMission() {
		// given
		MissionDetail quiz = saveQuizMission("퀴즈 미션");
		// when
		// then
		assertThatThrownBy(() -> submit(userId, UUID.randomUUID(), UUID.randomUUID(), allCorrect(quiz)))
				.satisfies(e -> assertMissionError(e, MissionErrorCode.MISSION_NOT_FOUND));
		assertThat(submissions(userId)).isZero();
	}
}
