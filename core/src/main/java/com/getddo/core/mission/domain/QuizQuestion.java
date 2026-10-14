package com.getddo.core.mission.domain;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
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
	/** 단답형 정답. 선택형이면 null. 관리자용 정보라 사용자 응답에 담지 않는다. */
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

	/** 이 문항의 선택지인지 확인한다. 다른 문항의 선택지는 거짓이다. */
	public boolean hasOption(UUID optionId) {
		return optionId != null && options.stream().anyMatch(option -> optionId.equals(option.getId()));
	}

	/**
	 * 답변이 정답인지 서버가 채점한다. 선택형은 고른 선택지가 정답 선택지일 때, 단답형은 입력 문자열이 정답과 같을 때 정답이다.
	 *
	 * <p>단답형은 유니코드 조합 형태(NFC)를 맞추고 앞뒤 공백을 지우며 연속 공백을 한 칸으로 줄이고 대소문자를 구분하지 않고
	 * 비교한다. 그 밖의 띄어쓰기·철자 차이는 오답이다. 이 정규화 규칙은 미션 채점 세부 계약의 구현 제안이다.</p>
	 */
	public boolean isCorrect(UUID selectedOptionId, String answerText) {
		if (questionType.isChoice()) {
			return selectedOptionId != null && options.stream()
					.anyMatch(option -> option.isCorrect() && selectedOptionId.equals(option.getId()));
		}
		return answerText != null && normalize(answerText).equals(normalize(correctAnswer));
	}

	private static String normalize(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ")
				.toLowerCase(Locale.ROOT);
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
