package com.getddo.core.entry.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.exception.EntryException;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntryRejectionsTest {

	@ParameterizedTest
	@EnumSource(value = EntryErrorCode.class, names = {"ADMIN_ENTRY_FORBIDDEN", "ENTRY_MEMBERSHIP_NOT_MET",
			"EVENT_NOT_OPEN", "ALREADY_ENTERED", "TICKET_LIMIT_EXCEEDED", "BRONZE_REQUIRED"})
	@DisplayName("스펙이 이력으로 남기는 응모 거절은 저장 대상이고 저장된 코드로 같은 예외를 복원한다")
	void recordableEntryRejectionsRoundTrip(EntryErrorCode code) {
		// given
		BusinessException failure = new EntryException(code);
		// when
		BusinessException restored = EntryRejections.restore(code.getCode());
		// then
		assertThat(EntryRejections.isRecordable(failure)).isTrue();
		assertThat(restored).isInstanceOf(EntryException.class);
		assertThat(restored.getErrorCode()).isEqualTo(code);
	}

	@Test
	@DisplayName("응모권 부족(TICKET-006)도 저장 대상이고 TicketException으로 복원한다")
	void ticketInsufficientRoundTrips() {
		// given
		BusinessException failure = new TicketException(TicketErrorCode.TICKET_INSUFFICIENT);
		// when
		BusinessException restored = EntryRejections.restore("TICKET-006");
		// then
		assertThat(EntryRejections.isRecordable(failure)).isTrue();
		assertThat(restored).isInstanceOf(TicketException.class);
		assertThat(restored.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_INSUFFICIENT);
	}

	@ParameterizedTest
	@EnumSource(value = EntryErrorCode.class, names = {"INVALID_ENTRY_REQUEST", "EVENT_NOT_FOUND",
			"IDEMPOTENCY_CONFLICT"})
	@DisplayName("요청 형식 오류·없는 이벤트·같은 키의 다른 요청은 저장 대상이 아니다")
	void nonRecordableEntryFailures(EntryErrorCode code) {
		assertThat(EntryRejections.isRecordable(new EntryException(code))).isFalse();
	}

	@Test
	@DisplayName("응모권 부족이 아닌 응모권 오류는 저장 대상이 아니고, 알 수 없는 코드는 복원하지 않는다")
	void otherTicketErrorsAndUnknownCodes() {
		// given
		// when
		// then
		assertThat(EntryRejections.isRecordable(new TicketException(TicketErrorCode.TICKET_INVALID_USE))).isFalse();
		assertThatThrownBy(() -> EntryRejections.restore("ENTRY-004")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> EntryRejections.restore("NOPE")).isInstanceOf(IllegalStateException.class);
	}
}
