package com.getddo.core.user.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.getddo.core.common.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

	USER_ID_REQUIRED(400, "USER-001", "사용자 ID가 필요합니다."),
	USER_NOT_FOUND(401, "USER-002", "사용자를 확인할 수 없습니다."),
	USER_CONTEXT_REQUIRED(401, "USER-003", "사용자 정보 헤더가 필요합니다."),
	USER_ROLE_MISMATCH(403, "USER-004", "사용자 역할이 일치하지 않습니다."),
	USER_MEMBERSHIP_REQUIRED(403, "USER-005", "사용자 멤버십을 확인할 수 없습니다."),
	USER_MEMBERSHIP_MISMATCH(409, "USER-006", "사용자 멤버십이 일치하지 않습니다."),
	USER_INACTIVE(403, "USER-007", "비활성 사용자는 내 정보를 조회할 수 없습니다.");

	private final int status;
	private final String code;
	private final String message;
}
