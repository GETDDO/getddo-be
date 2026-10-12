package com.getddo.core.mission.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.mission.exception.MissionErrorCode;
import com.getddo.core.mission.exception.MissionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionDetailTest {

	private static Mission mission(MissionType type) {
		return Mission.register(UUID.randomUUID(), UUID.randomUUID(), "제목", "설명", type,
				Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-31T00:00:00Z"), null);
	}

	private static QuizQuestion shortAnswer(int order) {
		return QuizQuestion.of(QuizQuestionType.SHORT_ANSWER, "문제", order, "정답", List.of());
	}

	private static SurveyQuestion freeText(int order) {
		return SurveyQuestion.of(SurveyQuestionType.FREE_TEXT, "질문", false, order, List.of());
	}

	@Test
	@DisplayName("퀴즈 미션은 퀴즈 문항을, 설문 미션은 설문 문항을 담는다")
	void holdsQuestionsOfMatchingType() {
		// given
		// when
		MissionDetail quiz = new MissionDetail(mission(MissionType.QUIZ), List.of(shortAnswer(1), shortAnswer(2)), List.of());
		MissionDetail survey = new MissionDetail(mission(MissionType.SURVEY), List.of(), List.of(freeText(1)));
		// then
		assertThat(quiz.getQuizQuestions()).hasSize(2);
		assertThat(survey.getSurveyQuestions()).hasSize(1);
	}

	@Test
	@DisplayName("미션 유형과 다른 종류의 문항이 있으면 구성 오류다")
	void rejectsQuestionsOfOtherType() {
		// given
		// when
		// then
		assertInvalid(() -> new MissionDetail(mission(MissionType.QUIZ), List.of(), List.of(freeText(1))));
		assertInvalid(() -> new MissionDetail(mission(MissionType.SURVEY), List.of(shortAnswer(1)), List.of()));
	}

	@Test
	@DisplayName("해당 유형의 문항이 하나도 없는 미션은 구성 오류다")
	void rejectsMissionWithoutQuestions() {
		// given
		// when
		// then
		assertInvalid(() -> new MissionDetail(mission(MissionType.QUIZ), List.of(), List.of()));
		assertInvalid(() -> new MissionDetail(mission(MissionType.SURVEY), List.of(), List.of()));
	}

	@Test
	@DisplayName("같은 미션의 문항 표시 순서가 겹치면 구성 오류다")
	void rejectsDuplicateDisplayOrder() {
		// given
		// when
		// then
		assertInvalid(() -> new MissionDetail(mission(MissionType.QUIZ), List.of(shortAnswer(1), shortAnswer(1)), List.of()));
		assertInvalid(() -> new MissionDetail(mission(MissionType.SURVEY), List.of(), List.of(freeText(2), freeText(2))));
	}

	private static void assertInvalid(Runnable action) {
		assertThatThrownBy(action::run)
				.isInstanceOfSatisfying(MissionException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(MissionErrorCode.INVALID_MISSION_DEFINITION));
	}
}
