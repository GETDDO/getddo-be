package com.getddo.core.user.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.getddo.core.common.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

	USER_ID_REQUIRED(400, "USER-001", "사용자 ID가 필요합니다."),
	USER_NOT_FOUND(401, "USER-002", "사용자를 확인할 수 없습니다."),
	USER_INACTIVE(403, "USER-007", "비활성 사용자는 내 정보를 조회할 수 없습니다."),
	INVALID_ADMIN_QUERY(400, "USER-008", "사용자 조회 조건이 올바르지 않습니다."),
	ADMIN_REQUIRED(403, "USER-009", "활성 관리자만 사용자 정보를 조회할 수 있습니다."),
	ADMIN_TARGET_NOT_FOUND(404, "USER-010", "사용자를 찾을 수 없습니다.");

	private final int status;
	private final String code;
	private final String message;
}
