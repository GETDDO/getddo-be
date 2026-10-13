package com.getddo.api.mission.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.getddo.core.mission.domain.MissionAnswerInput;

/**
 * 미션 제출 요청.
 *
 * <p>제출 ID는 본문이 아니라 {@code Idempotency-Key} 헤더(UUID)로 받는다.</p>
 *
 * @param answers 문항별 답변. 선택형 문항은 {@code selectedOptionId}만, 서술형 문항은 {@code answerText}만 보낸다. 퀴즈는 이미
 *        정답으로 완료한 문항을 빼고 일부 문항만 보내도 된다
 */
public record MissionSubmissionRequest(@NotEmpty List<@NotNull @Valid MissionAnswerRequest> answers) {

	/** 답변을 서비스 입력으로 옮긴다. */
	public List<MissionAnswerInput> toAnswers() {
		return answers.stream().map(MissionAnswerRequest::toInput).toList();
	}
}
