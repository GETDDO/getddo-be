package com.getddo.core.mission.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 퀴즈 문항. 단답형은 정답 문자열 하나를 갖고, OX와 단일 선택은 선택지에 정답 여부를 둔다.
 *
 * <p>선택형 정답의 개수와 OX 표현, 단답 문자열 정규화는 미션 채점 세부 계약이 정하므로 여기서 강제하지 않는다.</p>
 */
@Getter
public final class QuizQuestion {

	/** 저장 전에는 null. */
	private final UUID id;
	private final QuizQuestionType questionType;
	private final String questionText;
	private final int displayOrder;
	/** 단답형 정답. 선택형이면 null. */
	private final String correctAnswer;
	private final List<QuizOption> options;

	public QuizQuestion(UUID id, QuizQuestionType questionType, String questionText, int displayOrder,
			String correctAnswer, List<QuizOption> options) {
		this.id = id;
		this.questionType = Objects.requireNonNull(questionType, "questionType");
		this.questionText = Objects.requireNonNull(questionText, "questionText");
		this.displayOrder = displayOrder;
		this.correctAnswer = correctAnswer;
		this.options = List.copyOf(options);
		if (questionType.isChoice()) {
			// 선택지가 없으면 사용자가 답할 수 없다.
			MissionRules.definition(correctAnswer == null && !this.options.isEmpty());
		} else {
			MissionRules.definition(MissionRules.hasText(correctAnswer) && this.options.isEmpty());
		}
		MissionRules.definition(MissionRules.distinctOrders(this.options, QuizOption::getDisplayOrder));
	}

	/**
	 * 저장 전 문항을 만든다.
	 *
	 * @throws com.getddo.core.mission.exception.MissionException 문항이 비었거나, 단답형에 정답이 없거나 선택지가 있을 때,
	 *         선택형에 정답 문자열이 있거나 선택지가 없을 때, 선택지 순서가 겹칠 때
	 */
	public static QuizQuestion of(QuizQuestionType questionType, String questionText, int displayOrder,
			String correctAnswer, List<QuizOption> options) {
		Objects.requireNonNull(questionType, "questionType");
		MissionRules.definition(MissionRules.hasText(questionText));
		return new QuizQuestion(null, questionType, questionText, displayOrder, correctAnswer, options);
	}
}
