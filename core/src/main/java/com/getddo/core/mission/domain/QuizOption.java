package com.getddo.core.mission.domain;

import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** 퀴즈 선택지. 정답 여부는 관리자용 정보라 사용자 응답에 담지 않는다. */
@Getter
public final class QuizOption {

	/** 저장 전에는 null. */
	private final UUID id;
	private final String optionText;
	private final int displayOrder;
	private final boolean correct;

	public QuizOption(UUID id, String optionText, int displayOrder, boolean correct) {
		this.id = id;
		this.optionText = Objects.requireNonNull(optionText, "optionText");
		this.displayOrder = displayOrder;
		this.correct = correct;
	}

	/** 저장 전 선택지를 만든다. */
	public static QuizOption of(String optionText, int displayOrder, boolean correct) {
		MissionRules.definition(MissionRules.hasText(optionText));
		return new QuizOption(null, optionText, displayOrder, correct);
	}
}
