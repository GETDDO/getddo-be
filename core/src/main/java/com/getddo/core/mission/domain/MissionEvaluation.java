package com.getddo.core.mission.domain;

import java.util.List;
import java.util.UUID;

import lombok.Getter;

/**
 * 제출한 답변을 미션에 대조해 채점한 결과. 저장할 답변과 완료 여부, 이번 제출로 새로 정답이 된 문항을 담는다.
 */
@Getter
public final class MissionEvaluation {

	private final List<QuizAnswer> quizAnswers;
	private final List<SurveyAnswer> surveyAnswers;
	/** 이 제출로 미션을 완료했는지 여부. */
	private final boolean completed;
	/** 이번 제출로 처음 정답이 된 퀴즈 문항 ID. 잠금 순서를 고정하려고 오름차순으로 정렬한다. */
	private final List<UUID> newlyCorrectQuestionIds;

	public MissionEvaluation(List<QuizAnswer> quizAnswers, List<SurveyAnswer> surveyAnswers, boolean completed,
			List<UUID> newlyCorrectQuestionIds) {
		this.quizAnswers = List.copyOf(quizAnswers);
		this.surveyAnswers = List.copyOf(surveyAnswers);
		this.completed = completed;
		this.newlyCorrectQuestionIds = List.copyOf(newlyCorrectQuestionIds);
	}
}
