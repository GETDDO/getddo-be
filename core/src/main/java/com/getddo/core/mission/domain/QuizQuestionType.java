package com.getddo.core.mission.domain;

/** 퀴즈 문항 유형. */
public enum QuizQuestionType {
	OX,
	SINGLE_CHOICE,
	SHORT_ANSWER;

	/** 선택지로 답하는 유형인지 확인한다. */
	public boolean isChoice() {
		return this != SHORT_ANSWER;
	}
}
