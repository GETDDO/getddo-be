package com.getddo.core.mission.domain;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.mission.exception.MissionErrorCode;
import com.getddo.core.mission.exception.MissionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 제출한 답변을 미션의 문항에 대조해 채점하고 완료 여부를 판정하는 규칙을 검증한다. */
class MissionEvaluationTest {

	private static final Instant STARTS = Instant.parse("2026-10-01T00:00:00Z");

	private final UUID choiceOk = UUID.randomUUID();
	private final UUID choiceNo = UUID.randomUUID();
	private final UUID choiceQuestionId = UUID.randomUUID();
	private final UUID shortQuestionId = UUID.randomUUID();
	private final UUID oxQuestionId = UUID.randomUUID();
	private final UUID oxYes = UUID.randomUUID();
	private final UUID oxNo = UUID.randomUUID();

	private Mission mission(MissionType type) {
		return new Mission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "제목", "설명", type, STARTS,
				STARTS.plusSeconds(3600), MissionStatus.ACTIVE, null, STARTS, STARTS);
	}

	/** 단일 선택·단답·OX 세 문항짜리 퀴즈. */
	private MissionDetail quiz() {
		QuizQuestion choice = new QuizQuestion(choiceQuestionId, QuizQuestionType.SINGLE_CHOICE, "수도는?", 1, null,
				List.of(new QuizOption(choiceOk, "서울", 1, true), new QuizOption(choiceNo, "부산", 2, false)));
		QuizQuestion shortAnswer = new QuizQuestion(shortQuestionId, QuizQuestionType.SHORT_ANSWER, "브랜드는?", 2,
				"Get Ddo", List.of());
		QuizQuestion ox = new QuizQuestion(oxQuestionId, QuizQuestionType.OX, "참인가?", 3, null,
				List.of(new QuizOption(oxYes, "O", 1, true), new QuizOption(oxNo, "X", 2, false)));
		return new MissionDetail(mission(MissionType.QUIZ), List.of(choice, shortAnswer, ox), List.of());
	}

	private final UUID surveyChoiceQuestionId = UUID.randomUUID();
	private final UUID surveyTextQuestionId = UUID.randomUUID();
	private final UUID surveyOption = UUID.randomUUID();

	/** 필수 단일 선택 한 문항과 선택 자유 입력 한 문항짜리 설문. */
	private MissionDetail survey() {
		SurveyQuestion required = new SurveyQuestion(surveyChoiceQuestionId, SurveyQuestionType.SINGLE_CHOICE, "만족도는?",
				true, 1, List.of(new SurveyOption(surveyOption, "좋음", 1)));
		SurveyQuestion optional = new SurveyQuestion(surveyTextQuestionId, SurveyQuestionType.FREE_TEXT, "의견은?", false, 2,
				List.of());
		return new MissionDetail(mission(MissionType.SURVEY), List.of(), List.of(required, optional));
	}

	private static MissionAnswerInput option(UUID questionId, UUID optionId) {
		return new MissionAnswerInput(questionId, optionId, null);
	}

	private static MissionAnswerInput text(UUID questionId, String answerText) {
		return new MissionAnswerInput(questionId, null, answerText);
	}

	private List<MissionAnswerInput> allCorrect() {
		return List.of(option(choiceQuestionId, choiceOk), text(shortQuestionId, "get ddo"), option(oxQuestionId, oxYes));
	}

	@Test
	@DisplayName("퀴즈의 모든 문항에 정답이면 완료하고 정답 문항을 정해진 순서로 돌려준다")
	void completesWhenEveryQuestionIsCorrect() {
		// given
		MissionDetail detail = quiz();
		// when
		MissionEvaluation evaluation = detail.evaluate(allCorrect(), Set.of());
		// then
		assertThat(evaluation.isCompleted()).isTrue();
		assertThat(evaluation.getQuizAnswers()).hasSize(3).allMatch(QuizAnswer::isCorrect);
		assertThat(evaluation.getSurveyAnswers()).isEmpty();
		assertThat(evaluation.getNewlyCorrectQuestionIds())
				.containsExactlyInAnyOrder(choiceQuestionId, shortQuestionId, oxQuestionId)
				.isSorted();
	}

	@Test
	@DisplayName("오답이 있으면 완료하지 않고 맞힌 문항만 새로 정답으로 센다")
	void leavesIncompleteWhenAnyAnswerIsWrong() {
		// given
		MissionDetail detail = quiz();
		// when
		MissionEvaluation evaluation = detail.evaluate(List.of(option(choiceQuestionId, choiceNo),
				text(shortQuestionId, "Get Ddo"), option(oxQuestionId, oxYes)), Set.of());
		// then
		assertThat(evaluation.isCompleted()).isFalse();
		assertThat(evaluation.getQuizAnswers()).hasSize(3);
		assertThat(evaluation.getQuizAnswers()).filteredOn(a -> !a.isCorrect()).singleElement()
				.satisfies(a -> assertThat(a.getQuestionId()).isEqualTo(choiceQuestionId));
		assertThat(evaluation.getNewlyCorrectQuestionIds()).containsExactlyInAnyOrder(shortQuestionId, oxQuestionId);
	}

	@Test
	@DisplayName("이미 정답으로 완료한 문항은 누적되어 남은 문항만 맞히면 완료한다")
	void accumulatesQuestionsAnsweredCorrectlyBefore() {
		// given
		MissionDetail detail = quiz();
		Set<UUID> before = Set.of(choiceQuestionId, oxQuestionId);
		// when
		MissionEvaluation evaluation = detail.evaluate(List.of(text(shortQuestionId, "GET DDO")), before);
		// then
		assertThat(evaluation.isCompleted()).isTrue();
		assertThat(evaluation.getNewlyCorrectQuestionIds()).containsExactly(shortQuestionId);
		assertThat(evaluation.getQuizAnswers()).extracting(QuizAnswer::getQuestionId).containsExactly(shortQuestionId);
	}

	@Test
	@DisplayName("이미 정답으로 완료한 문항에 다시 보낸 답변은 채점·저장하지 않고 무시한다")
	void ignoresAnswersToQuestionsAlreadyCorrect() {
		// given
		MissionDetail detail = quiz();
		Set<UUID> before = Set.of(choiceQuestionId);
		// when
		MissionEvaluation evaluation = detail.evaluate(List.of(option(choiceQuestionId, choiceNo),
				text(shortQuestionId, "get ddo"), option(oxQuestionId, oxYes)), before);
		// then
		assertThat(evaluation.isCompleted()).isTrue();
		assertThat(evaluation.getQuizAnswers()).extracting(QuizAnswer::getQuestionId)
				.containsExactlyInAnyOrder(shortQuestionId, oxQuestionId);
	}

	@Test
	@DisplayName("보낸 답변이 모두 이미 정답으로 완료한 문항이면 처리할 답변이 없어 거절한다")
	void rejectsWhenNothingLeftToGrade() {
		// given
		MissionDetail detail = quiz();
		// when
		// then
		assertInvalid(() -> detail.evaluate(List.of(option(choiceQuestionId, choiceOk)), Set.of(choiceQuestionId)));
	}

	@Test
	@DisplayName("단답형은 앞뒤 공백·연속 공백·대소문자·유니코드 조합 차이를 무시하고 비교한다")
	void normalizesShortAnswers() {
		// given
		QuizQuestion question = new QuizQuestion(shortQuestionId, QuizQuestionType.SHORT_ANSWER, "문제", 1, "서울 시청",
				List.of());
		// when
		// then
		assertThat(question.isCorrect(null, "  서울   시청 ")).isTrue();
		assertThat(question.isCorrect(null, "서울\t시청")).isTrue();
		assertThat(question.isCorrect(null, "서울시청")).isFalse();
		assertThat(question.isCorrect(null, "부산 시청")).isFalse();
		String composed = "한글";
		String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);
		assertThat(decomposed).isNotEqualTo(composed);
		QuizQuestion hangul = new QuizQuestion(shortQuestionId, QuizQuestionType.SHORT_ANSWER, "문제", 1, composed, List.of());
		assertThat(hangul.isCorrect(null, decomposed)).isTrue();
		assertThat(new QuizQuestion(shortQuestionId, QuizQuestionType.SHORT_ANSWER, "문제", 1, decomposed, List.of())
				.isCorrect(null, composed)).isTrue();
	}

	@Test
	@DisplayName("선택형은 고른 선택지가 정답 선택지일 때만 정답이고 다른 문항의 선택지는 이 문항의 선택지가 아니다")
	void gradesChoiceBySelectedOption() {
		// given
		QuizQuestion question = quiz().getQuizQuestions().get(0);
		// when
		// then
		assertThat(question.isCorrect(choiceOk, null)).isTrue();
		assertThat(question.isCorrect(choiceNo, null)).isFalse();
		assertThat(question.isCorrect(null, null)).isFalse();
		assertThat(question.hasOption(choiceOk)).isTrue();
		assertThat(question.hasOption(oxYes)).isFalse();
	}

	@Test
	@DisplayName("미션에 없는 문항, 다른 문항의 선택지, 선택지와 입력을 함께 보낸 답변, 같은 문항 중복, 빈 요청은 거절한다")
	void rejectsMalformedQuizAnswers() {
		// given
		MissionDetail detail = quiz();
		// when
		// then
		assertInvalid(() -> detail.evaluate(List.of(option(UUID.randomUUID(), choiceOk)), Set.of()));
		assertInvalid(() -> detail.evaluate(List.of(option(choiceQuestionId, oxYes)), Set.of()));
		assertInvalid(() -> detail.evaluate(List.of(new MissionAnswerInput(choiceQuestionId, choiceOk, "서울")), Set.of()));
		assertInvalid(() -> detail.evaluate(List.of(text(choiceQuestionId, "서울")), Set.of()));
		assertInvalid(() -> detail.evaluate(List.of(option(shortQuestionId, choiceOk)), Set.of()));
		assertInvalid(() -> detail.evaluate(List.of(text(shortQuestionId, " ")), Set.of()));
		assertInvalid(() -> detail.evaluate(List.of(option(oxQuestionId, oxYes), option(oxQuestionId, oxNo)), Set.of()));
		assertInvalid(() -> detail.evaluate(List.of(), Set.of()));
		assertInvalid(() -> detail.evaluate(null, Set.of()));
	}

	@Test
	@DisplayName("설문은 모든 필수 문항에 답해야 완료하고 선택 문항은 비워 둘 수 있다")
	void surveyRequiresEveryRequiredQuestion() {
		// given
		MissionDetail detail = survey();
		// when
		MissionEvaluation onlyRequired = detail.evaluate(List.of(option(surveyChoiceQuestionId, surveyOption)), Set.of());
		MissionEvaluation both = detail.evaluate(List.of(option(surveyChoiceQuestionId, surveyOption),
				text(surveyTextQuestionId, "좋아요")), Set.of());
		// then
		assertThat(onlyRequired.isCompleted()).isTrue();
		assertThat(onlyRequired.getSurveyAnswers()).hasSize(1);
		assertThat(onlyRequired.getQuizAnswers()).isEmpty();
		assertThat(onlyRequired.getNewlyCorrectQuestionIds()).isEmpty();
		assertThat(both.getSurveyAnswers()).hasSize(2);
		assertInvalid(() -> detail.evaluate(List.of(text(surveyTextQuestionId, "의견만")), Set.of()));
	}

	@Test
	@DisplayName("설문의 알 수 없는 문항·선택지, 빈 입력, 선택지와 입력을 함께 보낸 답변, 같은 문항 중복은 거절한다")
	void rejectsMalformedSurveyAnswers() {
		// given
		MissionDetail detail = survey();
		// when
		// then
		assertInvalid(() -> detail.evaluate(List.of(option(UUID.randomUUID(), surveyOption)), Set.of()));
		assertInvalid(() -> detail.evaluate(List.of(option(surveyChoiceQuestionId, UUID.randomUUID())), Set.of()));
		assertInvalid(() -> detail.evaluate(List.of(option(surveyChoiceQuestionId, surveyOption),
				text(surveyTextQuestionId, " ")), Set.of()));
		assertInvalid(() -> detail.evaluate(List.of(new MissionAnswerInput(surveyChoiceQuestionId, surveyOption, "좋음")),
				Set.of()));
		assertInvalid(() -> detail.evaluate(List.of(option(surveyChoiceQuestionId, surveyOption),
				option(surveyChoiceQuestionId, surveyOption)), Set.of()));
		assertInvalid(() -> detail.evaluate(List.of(text(surveyChoiceQuestionId, "좋음")), Set.of()));
	}

	private static void assertInvalid(Runnable action) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(MissionException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(MissionErrorCode.INVALID_MISSION_ANSWER));
	}
}
