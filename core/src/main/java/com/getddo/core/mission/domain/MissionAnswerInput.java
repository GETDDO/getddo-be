package com.getddo.core.mission.domain;

import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 사용자가 한 문항에 보낸 답변 원본. 아직 미션의 문항과 대조하지 않았으므로 채점 결과가 없다.
 *
 * <p>선택형 문항은 선택지만, 서술형 문항은 입력 문자열만 보낸다. 형태와 소속은 {@link MissionDetail#evaluate}가 검증한다.</p>
 */
@Getter
public final class MissionAnswerInput {

	private final UUID questionId;
	/** 선택형 문항에서만 보낸다. */
	private final UUID selectedOptionId;
	/** 서술형 문항에서만 보낸다. */
	private final String answerText;

	public MissionAnswerInput(UUID questionId, UUID selectedOptionId, String answerText) {
		this.questionId = Objects.requireNonNull(questionId, "questionId");
		this.selectedOptionId = selectedOptionId;
		this.answerText = answerText;
	}
}
