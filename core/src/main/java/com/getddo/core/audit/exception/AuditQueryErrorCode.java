package com.getddo.core.audit.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.getddo.core.common.exception.ErrorCode;

/** 관리자 감사 조회에서 확정한 조건·권한·상세 부재 오류다. */
@Getter
@RequiredArgsConstructor
public enum AuditQueryErrorCode implements ErrorCode {
	INVALID_QUERY(400, "AUDIT-001", "감사 로그 조회 조건이 올바르지 않습니다."),
	ADMIN_REQUIRED(403, "AUDIT-002", "활성 관리자만 감사 로그를 조회할 수 있습니다."),
	NOT_FOUND(404, "AUDIT-003", "감사 로그를 찾을 수 없습니다.");

	private final int status;
	private final String code;
	private final String message;
}
