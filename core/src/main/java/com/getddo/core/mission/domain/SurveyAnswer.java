package com.getddo.core.mission.domain;

import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** 설문 문항에 대한 답변. 단일 선택은 선택지만, 자유 입력은 입력 문자열만 갖는다. */
@Getter
public final class SurveyAnswer {

	/** 저장 전에는 null. */
	private final UUID id;
	private final UUID questionId;
	/** 단일 선택에서만 있다. */
	private final UUID selectedOptionId;
	/** 자유 입력에서만 있다. */
	private final String answerText;

	public SurveyAnswer(UUID id, UUID questionId, UUID selectedOptionId, String answerText) {
		this.id = id;
		this.questionId = Objects.requireNonNull(questionId, "questionId");
		this.selectedOptionId = selectedOptionId;
		this.answerText = answerText;
	}

	/**
	 * 저장 전 답변을 만든다.
	 *
	 * @throws com.getddo.core.mission.exception.MissionException 선택지와 입력 문자열 중 정확히 하나가 아닐 때
	 */
	public static SurveyAnswer of(UUID questionId, UUID selectedOptionId, String answerText) {
		MissionRules.answer(selectedOptionId != null ? answerText == null : MissionRules.hasText(answerText));
		return new SurveyAnswer(null, questionId, selectedOptionId, answerText);
	}
}
