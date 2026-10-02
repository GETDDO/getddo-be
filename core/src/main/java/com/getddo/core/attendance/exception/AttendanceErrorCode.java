package com.getddo.core.attendance.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.getddo.core.common.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum AttendanceErrorCode implements ErrorCode {
	/**
	 * 출석 시각에 적용할 일일 보상 정책이나 그 달의 연속 출석 정책 묶음이 없는 경우.
	 * 사용자가 해결할 수 없는 운영 설정 누락이라 서버 오류로 응답한다.
	 */
	ATTENDANCE_POLICY_NOT_FOUND(500, "ATTENDANCE-001", "출석 보상 정책이 설정되지 않았습니다.");

	private final int status;
	private final String code;
	private final String message;
}
