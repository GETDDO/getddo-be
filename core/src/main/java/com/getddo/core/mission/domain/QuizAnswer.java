package com.getddo.core.mission.domain;

import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 퀴즈 문항에 대한 답변과 서버 채점 결과. 선택형은 선택지만, 단답형은 입력 문자열만 갖는다.
 *
 * <p>채점은 서비스가 하고 이 객체는 결과를 기록한다. 소속 제출은 저장할 때 연결하므로 제출 ID를 갖지 않는다.</p>
 */
@Getter
public final class QuizAnswer {

	/** 저장 전에는 null. */
	private final UUID id;
	private final UUID questionId;
	/** 선택형에서만 있다. */
	private final UUID selectedOptionId;
	/** 단답형에서만 있다. */
	private final String answerText;
	private final boolean correct;

	public QuizAnswer(UUID id, UUID questionId, UUID selectedOptionId, String answerText, boolean correct) {
		this.id = id;
		this.questionId = Objects.requireNonNull(questionId, "questionId");
		this.selectedOptionId = selectedOptionId;
		this.answerText = answerText;
		this.correct = correct;
		// 저장소가 복원하는 경로도 팩터리와 같게 선택지나 입력 문자열 중 정확히 하나만 허용한다.
		MissionRules.answer(selectedOptionId != null ? answerText == null : MissionRules.hasText(answerText));
	}

	/**
	 * 저장 전 답변을 만든다.
	 *
	 * @throws com.getddo.core.mission.exception.MissionException 선택지와 입력 문자열 중 정확히 하나가 아닐 때
	 */
	public static QuizAnswer of(UUID questionId, UUID selectedOptionId, String answerText, boolean correct) {
		return new QuizAnswer(null, questionId, selectedOptionId, answerText, correct);
	}
}
