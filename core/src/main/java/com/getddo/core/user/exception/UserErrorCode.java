package com.getddo.core.user.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.getddo.core.common.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

	USER_ID_REQUIRED(400, "USER-001", "사용자 ID가 필요합니다."),
	USER_NOT_FOUND(401, "USER-002", "사용자를 확인할 수 없습니다."),
	USER_INACTIVE(403, "USER-007", "비활성 사용자는 내 정보를 조회할 수 없습니다.");

	private final int status;
	private final String code;
	private final String message;
}
