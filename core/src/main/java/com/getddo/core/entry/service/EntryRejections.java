package com.getddo.core.entry.service;

import java.util.List;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.exception.ErrorCode;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.exception.EntryException;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;

/**
 * 이력으로 남기는 업무 거절과 저장된 거절 코드의 복원.
 *
 * <p>스펙이 저장하도록 한 거절(관리자, 멤버십 미달, 기간 밖, 이미 응모, 누적 상한, 브론즈 부족, 응모권 부족)만 대상이다.
 * 요청 형식 오류, 없는 이벤트, 같은 키의 다른 요청은 이력으로 남기지 않는다.</p>
 */
final class EntryRejections {

	private static final List<ErrorCode> RECORDABLE = List.of(
			EntryErrorCode.ADMIN_ENTRY_FORBIDDEN,
			EntryErrorCode.ENTRY_MEMBERSHIP_NOT_MET,
			EntryErrorCode.EVENT_NOT_OPEN,
			EntryErrorCode.ALREADY_ENTERED,
			EntryErrorCode.TICKET_LIMIT_EXCEEDED,
			EntryErrorCode.BRONZE_REQUIRED,
			TicketErrorCode.TICKET_INSUFFICIENT);

	private EntryRejections() {
	}

	/** 실패를 거절 이력으로 남겨야 하는지 확인한다. */
	static boolean isRecordable(BusinessException failure) {
		return RECORDABLE.contains(failure.getErrorCode());
	}

	/**
	 * 저장된 거절 코드로 처음과 같은 예외를 만든다.
	 *
	 * @throws IllegalStateException 저장 대상이 아닌 코드인 경우
	 */
	static BusinessException restore(String code) {
		ErrorCode errorCode = RECORDABLE.stream().filter(candidate -> candidate.getCode().equals(code)).findFirst()
				.orElseThrow(() -> new IllegalStateException("알 수 없는 응모 거절 코드: " + code));
		return errorCode instanceof EntryErrorCode entry ? new EntryException(entry)
				: new TicketException((TicketErrorCode) errorCode);
	}
}
