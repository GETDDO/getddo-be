package com.getddo.core.mission.domain;

import java.util.List;
import java.util.Objects;

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
}
