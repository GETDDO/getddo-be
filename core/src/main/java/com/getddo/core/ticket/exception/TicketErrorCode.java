package com.getddo.core.ticket.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.getddo.core.common.exception.ErrorCode;

/**
 * 응모권 도메인 오류.
 *
 * <p>지급 오류는 호출자(미션·출석·게임)의 프로그래밍 오류를 가리키므로 메시지에 청구 내용이나 내부 값을 넣지 않는다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum TicketErrorCode implements ErrorCode {
	/** 지급 수량이 1 미만이거나 사유가 비어 있는 등 지급 요청 형식이 올바르지 않은 경우. */
	TICKET_INVALID_GRANT(400, "TICKET-001", "응모권 지급 요청이 올바르지 않습니다."),
	/** 지급 근거 청구 행이 없는 경우. 호출자가 청구를 먼저 저장하지 않았다는 뜻이다. */
	TICKET_GRANT_SOURCE_NOT_FOUND(404, "TICKET-002", "응모권 지급 근거를 찾을 수 없습니다."),
	/** 청구 행의 사용자 또는 수량이 지급 요청과 다른 경우. */
	TICKET_GRANT_SOURCE_MISMATCH(409, "TICKET-003", "응모권 지급 근거가 요청과 일치하지 않습니다."),
	/** 이력 조회의 커서 형식, 조회 개수, 기간 조건이 올바르지 않은 경우. */
	TICKET_INVALID_LEDGER_QUERY(400, "TICKET-004", "응모권 이력 조회 조건이 올바르지 않습니다.");

	private final int status;
	private final String code;
	private final String message;
}
