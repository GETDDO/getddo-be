package com.getddo.core.mission.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** 설문 문항. 필수 여부를 갖는다. 퀴즈의 정답과는 별개의 개념이다. */
@Getter
public final class SurveyQuestion {

	/** 저장 전에는 null. */
	private final UUID id;
	private final SurveyQuestionType questionType;
	private final String questionText;
	private final boolean required;
	private final int displayOrder;
	private final List<SurveyOption> options;

	public SurveyQuestion(UUID id, SurveyQuestionType questionType, String questionText, boolean required,
			int displayOrder, List<SurveyOption> options) {
		this.id = id;
		this.questionType = Objects.requireNonNull(questionType, "questionType");
		this.questionText = Objects.requireNonNull(questionText, "questionText");
		this.required = required;
		this.displayOrder = displayOrder;
		this.options = List.copyOf(options);
	}

	/**
	 * 저장 전 문항을 만든다.
	 *
	 * @throws com.getddo.core.mission.exception.MissionException 문항이 비었거나, 자유 입력에 선택지가 있거나, 선택지 순서가 겹칠 때
	 */
	public static SurveyQuestion of(SurveyQuestionType questionType, String questionText, boolean required,
			int displayOrder, List<SurveyOption> options) {
		Objects.requireNonNull(questionType, "questionType");
		MissionRules.definition(MissionRules.hasText(questionText));
		MissionRules.definition(questionType == SurveyQuestionType.SINGLE_CHOICE || options.isEmpty());
		MissionRules.definition(MissionRules.distinctOrders(options, SurveyOption::getDisplayOrder));
		return new SurveyQuestion(null, questionType, questionText, required, displayOrder, options);
	}
}
