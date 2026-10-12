package com.getddo.core.mission.domain;

import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** 설문 선택지. */
@Getter
public final class SurveyOption {

	/** 저장 전에는 null. */
	private final UUID id;
	private final String optionText;
	private final int displayOrder;

	public SurveyOption(UUID id, String optionText, int displayOrder) {
		this.id = id;
		this.optionText = Objects.requireNonNull(optionText, "optionText");
		this.displayOrder = displayOrder;
	}

	/** 저장 전 선택지를 만든다. */
	public static SurveyOption of(String optionText, int displayOrder) {
		MissionRules.definition(MissionRules.hasText(optionText));
		return new SurveyOption(null, optionText, displayOrder);
	}
}
