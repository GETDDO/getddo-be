package com.getddo.core.mission.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.Getter;

/**
 * 문항까지 포함한 미션. 퀴즈 미션은 퀴즈 문항만, 설문 미션은 설문 문항만 갖는다.
 *
 * <p>미션 등록(문항·선택지를 함께 저장)과 상세 조회가 같은 형태를 쓴다.</p>
 */
@Getter
public final class MissionDetail {

	private final Mission mission;
	private final List<QuizQuestion> quizQuestions;
	private final List<SurveyQuestion> surveyQuestions;

	/**
	 * @throws com.getddo.core.mission.exception.MissionException 미션 유형과 다른 종류의 문항이 있거나, 해당 유형의 문항이 없거나,
	 *         문항 순서가 겹칠 때
	 */
	public MissionDetail(Mission mission, List<QuizQuestion> quizQuestions, List<SurveyQuestion> surveyQuestions) {
		this.mission = Objects.requireNonNull(mission, "mission");
		this.quizQuestions = List.copyOf(quizQuestions);
		this.surveyQuestions = List.copyOf(surveyQuestions);
		// 문항이 없으면 설문은 답변 없이 완료되고 퀴즈는 완료할 수 없다.
		MissionRules.definition(mission.getMissionType() == MissionType.QUIZ
				? this.surveyQuestions.isEmpty() && !this.quizQuestions.isEmpty()
				: this.quizQuestions.isEmpty() && !this.surveyQuestions.isEmpty());
		MissionRules.definition(MissionRules.distinctOrders(this.quizQuestions, QuizQuestion::getDisplayOrder));
		MissionRules.definition(MissionRules.distinctOrders(this.surveyQuestions, SurveyQuestion::getDisplayOrder));
	}
	/**
	 * 제출한 답변을 이 미션의 문항에 대조해 채점하고 완료 여부를 판정한다. 저장된 미션(문항 ID가 있는)에만 쓴다.
	 *
	 * <p>퀴즈는 문항별 정답을 누적한다. 이미 정답으로 완료한 문항({@code alreadyCorrectQuestionIds})에 다시 보낸 답변은 채점·저장하지
	 * 않고 무시한다. 남은 문항을 모두 맞히면 완료하고, 일부만 보내거나 틀린 문항이 있으면 완료하지 않는다. 설문은 모든 필수 문항에
	 * 답해야 하며, 필수 문항이 비면 완료하지 않고 거절한다. 입력 문자열이 공백뿐이면 보내지 않은 것으로 본다.</p>
	 *
	 * @param inputs                   사용자가 보낸 답변. 비어 있으면 안 된다
	 * @param alreadyCorrectQuestionIds 사용자가 이전 제출에서 이미 정답으로 완료한 퀴즈 문항 ID. 설문에서는 쓰지 않는다
	 * @throws com.getddo.core.mission.exception.MissionException 이 미션에 없는 문항, 문항에 속하지 않는 선택지, 문항 유형과 맞지 않는
	 *         답변 형태, 같은 문항 중복, 처리할 답변이 없음, 설문의 필수 문항 누락
	 */
	public MissionEvaluation evaluate(List<MissionAnswerInput> inputs, Set<UUID> alreadyCorrectQuestionIds) {
		MissionRules.answer(inputs != null && !inputs.isEmpty());
		List<UUID> questionIds = inputs.stream().map(MissionAnswerInput::getQuestionId).toList();
		MissionRules.answer(new HashSet<>(questionIds).size() == questionIds.size());
		return mission.getMissionType() == MissionType.QUIZ
				? evaluateQuiz(inputs, alreadyCorrectQuestionIds)
				: evaluateSurvey(inputs);
	}

	private MissionEvaluation evaluateQuiz(List<MissionAnswerInput> inputs, Set<UUID> alreadyCorrect) {
		Map<UUID, QuizQuestion> questions = quizQuestions.stream()
				.collect(Collectors.toMap(QuizQuestion::getId, Function.identity()));
		List<QuizAnswer> answers = new ArrayList<>();
		Set<UUID> correctNow = new TreeSet<>();
		for (MissionAnswerInput input : inputs) {
			QuizQuestion question = questions.get(input.getQuestionId());
			MissionRules.answer(question != null);
			if (alreadyCorrect.contains(question.getId())) {
				continue;
			}
			UUID selected = input.getSelectedOptionId();
			String text = textOrNull(input.getAnswerText());
			if (question.getQuestionType().isChoice()) {
				MissionRules.answer(selected != null && question.hasOption(selected) && text == null);
			} else {
				MissionRules.answer(selected == null && text != null);
			}
			boolean correct = question.isCorrect(selected, text);
			answers.add(QuizAnswer.of(question.getId(), selected, text, correct));
			if (correct) {
				correctNow.add(question.getId());
			}
		}
		MissionRules.answer(!answers.isEmpty());
		boolean completed = quizQuestions.stream()
				.allMatch(question -> alreadyCorrect.contains(question.getId()) || correctNow.contains(question.getId()));
		return new MissionEvaluation(answers, List.of(), completed, List.copyOf(correctNow));
	}

	private MissionEvaluation evaluateSurvey(List<MissionAnswerInput> inputs) {
		Map<UUID, SurveyQuestion> questions = surveyQuestions.stream()
				.collect(Collectors.toMap(SurveyQuestion::getId, Function.identity()));
		List<SurveyAnswer> answers = new ArrayList<>();
		for (MissionAnswerInput input : inputs) {
			SurveyQuestion question = questions.get(input.getQuestionId());
			MissionRules.answer(question != null);
			UUID selected = input.getSelectedOptionId();
			String text = textOrNull(input.getAnswerText());
			if (question.getQuestionType() == SurveyQuestionType.SINGLE_CHOICE) {
				MissionRules.answer(selected != null && question.hasOption(selected) && text == null);
			} else {
				MissionRules.answer(selected == null && text != null);
			}
			answers.add(SurveyAnswer.of(question.getId(), selected, text));
		}
		Set<UUID> answered = new HashSet<>(inputs.stream().map(MissionAnswerInput::getQuestionId).toList());
		MissionRules.answer(surveyQuestions.stream()
				.filter(SurveyQuestion::isRequired)
				.allMatch(question -> answered.contains(question.getId())));
		return new MissionEvaluation(List.of(), answers, true, List.of());
	}

	private static String textOrNull(String text) {
		return MissionRules.hasText(text) ? text : null;
	}
}
