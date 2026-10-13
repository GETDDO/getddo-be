package com.getddo.core.mission.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.getddo.core.common.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum MissionErrorCode implements ErrorCode {
	/** 미션의 기간·문항·선택지·정답 구성이 규칙에 맞지 않는 경우. */
	INVALID_MISSION_DEFINITION(400, "MISSION-001", "미션 구성이 올바르지 않습니다."),
	/** 제출한 답변의 형태가 미션 유형이나 문항 유형과 맞지 않는 경우. */
	INVALID_MISSION_ANSWER(400, "MISSION-002", "미션 답변 형식이 올바르지 않습니다.");

	private final int status;
	private final String code;
	private final String message;
}
