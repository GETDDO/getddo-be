package com.getddo.core.event.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.getddo.core.common.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum EventErrorCode implements ErrorCode {

	INVALID_DETAILS(400, "EVENT-001", "이벤트 정보가 올바르지 않습니다."),
	INVALID_PERIOD(400, "EVENT-002", "이벤트 기간이 올바르지 않습니다."),
	INVALID_CONFIGURATION(400, "EVENT-003", "이벤트 응모 조건이 올바르지 않습니다."),
	INVALID_PRIZES(400, "EVENT-004", "경품 구성이 올바르지 않습니다."),
	USER_CONTEXT_REQUIRED(401, "EVENT-005", "등록된 사용자 정보가 필요합니다."),
	ADMIN_REQUIRED(403, "EVENT-006", "관리자만 이벤트를 등록할 수 있습니다."),
	EVENT_NOT_FOUND(404, "EVENT-007", "이벤트를 찾을 수 없습니다."),
	INVALID_QUERY(400, "EVENT-008", "이벤트 조회 조건이 올바르지 않습니다."),
	MEMBERSHIP_MISMATCH(409, "EVENT-009", "선택한 멤버십이 사용자 정보와 일치하지 않습니다."),
	ACCESS_DENIED(403, "EVENT-010", "이벤트 조회 권한이 없습니다.");

	private final int status;
	private final String code;
	private final String message;
}
