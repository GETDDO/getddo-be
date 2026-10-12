package com.getddo.core.mission.domain;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.mission.exception.MissionErrorCode;
import com.getddo.core.mission.exception.MissionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionTest {

	private static List<QuizOption> yesNo() {
		return List.of(QuizOption.of("O", 1, true), QuizOption.of("X", 2, false));
	}

	@Test
	@DisplayName("단답형 퀴즈는 정답 문자열이 필요하고 선택지를 가질 수 없다")
	void shortAnswerShape() {
		assertThat(QuizQuestion.of(QuizQuestionType.SHORT_ANSWER, "문제", 1, "정답", List.of()).getCorrectAnswer())
				.isEqualTo("정답");
		assertInvalid(() -> QuizQuestion.of(QuizQuestionType.SHORT_ANSWER, "문제", 1, null, List.of()));
		assertInvalid(() -> QuizQuestion.of(QuizQuestionType.SHORT_ANSWER, "문제", 1, " ", List.of()));
		assertInvalid(() -> QuizQuestion.of(QuizQuestionType.SHORT_ANSWER, "문제", 1, "정답", yesNo()));
	}

	@Test
	@DisplayName("선택형 퀴즈는 정답 문자열을 가질 수 없고 정답은 선택지로 표현한다")
	void choiceShape() {
		assertThat(QuizQuestion.of(QuizQuestionType.OX, "문제", 1, null, yesNo()).getOptions()).hasSize(2);
		assertThat(QuizQuestion.of(QuizQuestionType.SINGLE_CHOICE, "문제", 1, null, yesNo()).getOptions())
				.anyMatch(QuizOption::isCorrect);
		assertInvalid(() -> QuizQuestion.of(QuizQuestionType.OX, "문제", 1, "O", yesNo()));
	}

	@Test
	@DisplayName("문제 내용과 선택지 내용이 비어 있으면 구성 오류다")
	void textRequired() {
		assertInvalid(() -> QuizQuestion.of(QuizQuestionType.OX, " ", 1, null, yesNo()));
		assertInvalid(() -> QuizOption.of(" ", 1, true));
		assertInvalid(() -> SurveyQuestion.of(SurveyQuestionType.FREE_TEXT, "", true, 1, List.of()));
		assertInvalid(() -> SurveyOption.of("", 1));
	}

	@Test
	@DisplayName("한 문항 안의 선택지 표시 순서가 겹치면 구성 오류다")
	void duplicateOptionOrder() {
		assertInvalid(() -> QuizQuestion.of(QuizQuestionType.SINGLE_CHOICE, "문제", 1, null,
				List.of(QuizOption.of("가", 1, true), QuizOption.of("나", 1, false))));
		assertInvalid(() -> SurveyQuestion.of(SurveyQuestionType.SINGLE_CHOICE, "질문", true, 1,
				List.of(SurveyOption.of("가", 1), SurveyOption.of("나", 1))));
	}

	@Test
	@DisplayName("자유 입력 설문은 선택지를 가질 수 없고 단일 선택 설문은 선택지를 가진다")
	void surveyShape() {
		assertThat(SurveyQuestion.of(SurveyQuestionType.SINGLE_CHOICE, "질문", true, 1,
				List.of(SurveyOption.of("가", 1))).getOptions()).hasSize(1);
		assertInvalid(() -> SurveyQuestion.of(SurveyQuestionType.FREE_TEXT, "질문", false, 1,
				List.of(SurveyOption.of("가", 1))));
	}

	private static void assertInvalid(Runnable action) {
		assertThatThrownBy(action::run)
				.isInstanceOfSatisfying(MissionException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(MissionErrorCode.INVALID_MISSION_DEFINITION));
	}
}
