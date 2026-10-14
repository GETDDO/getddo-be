package com.getddo.api.mission.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import com.getddo.core.mission.domain.MissionAnswerInput;

/**
 * 한 문항에 대한 답변.
 *
 * @param questionId 답하는 문항
 * @param selectedOptionId 선택형 문항(OX·단일 선택)에서 고른 선택지
 * @param answerText 서술형 문항(단답·자유 입력)에서 입력한 문자열. 공백뿐이면 보내지 않은 것으로 본다
 */
public record MissionAnswerRequest(@NotNull UUID questionId, UUID selectedOptionId, String answerText) {

	/** 서비스 입력으로 옮긴다. 문항·선택지 소속과 형태는 서비스가 미션과 대조해 검증한다. */
	public MissionAnswerInput toInput() {
		return new MissionAnswerInput(questionId, selectedOptionId, answerText);
	}
}
