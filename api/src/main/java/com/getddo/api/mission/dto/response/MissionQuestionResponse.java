package com.getddo.api.mission.dto.response;

import java.util.List;
import java.util.UUID;

import lombok.Builder;

import com.getddo.core.mission.domain.QuizOption;
import com.getddo.core.mission.domain.QuizQuestion;
import com.getddo.core.mission.domain.SurveyOption;
import com.getddo.core.mission.domain.SurveyQuestion;

/**
 * 미션 문항이다. 퀴즈와 설문의 문항을 같은 모양으로 보여 준다.
 *
 * <p>사용자 응답에는 정답({@code correctAnswer}, 선택지의 정답 여부)을 담지 않는다. 변환은 이 클래스가 정답 필드를 읽지
 * 않도록 문항 ID·유형·본문·순서·선택지 본문만 옮긴다. 퀴즈는 완료하려면 모든 문항을 맞혀야 하므로 {@code required}가 항상
 * true이고, 설문은 문항에 저장된 필수 여부를 따른다.</p>
 *
 * @param questionType 퀴즈는 OX·SINGLE_CHOICE·SHORT_ANSWER, 설문은 SINGLE_CHOICE·FREE_TEXT
 * @param options 선택형 문항의 선택지. 서술형 문항이면 빈 목록이다
 */
@Builder
public record MissionQuestionResponse(
		UUID id,
		String questionType,
		String questionText,
		boolean required,
		int displayOrder,
		List<QuestionOptionResponse> options) {

	/** 퀴즈 문항을 정답 정보 없이 옮긴다. */
	public static MissionQuestionResponse from(QuizQuestion question) {
		return MissionQuestionResponse.builder()
				.id(question.getId())
				.questionType(question.getQuestionType().name())
				.questionText(question.getQuestionText())
				.required(true)
				.displayOrder(question.getDisplayOrder())
				.options(question.getOptions().stream().map(MissionQuestionResponse::option).toList())
				.build();
	}

	/** 설문 문항을 옮긴다. */
	public static MissionQuestionResponse from(SurveyQuestion question) {
		return MissionQuestionResponse.builder()
				.id(question.getId())
				.questionType(question.getQuestionType().name())
				.questionText(question.getQuestionText())
				.required(question.isRequired())
				.displayOrder(question.getDisplayOrder())
				.options(question.getOptions().stream().map(MissionQuestionResponse::option).toList())
				.build();
	}

	private static QuestionOptionResponse option(QuizOption option) {
		return QuestionOptionResponse.builder()
				.id(option.getId())
				.optionText(option.getOptionText())
				.displayOrder(option.getDisplayOrder())
				.build();
	}

	private static QuestionOptionResponse option(SurveyOption option) {
		return QuestionOptionResponse.builder()
				.id(option.getId())
				.optionText(option.getOptionText())
				.displayOrder(option.getDisplayOrder())
				.build();
	}
}
