package com.getddo.core.ticket.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketErrorCodeTest {

	@Test
	@DisplayName("이력 조회 조건 오류는 TICKET-004, 400이다")
	void historyQueryError() {
		// given
		// when
		// then
		assertThat(TicketErrorCode.TICKET_INVALID_HISTORY_QUERY.getCode()).isEqualTo("TICKET-004");
		assertThat(TicketErrorCode.TICKET_INVALID_HISTORY_QUERY.getStatus()).isEqualTo(400);
	}

	@Test
	@DisplayName("지급 오류의 코드와 HTTP 상태는 설계 문서 §6과 같다")
	void matchesDesignedCodes() {
		// given
		// when
		// then
		assertThat(TicketErrorCode.TICKET_INVALID_GRANT.getCode()).isEqualTo("TICKET-001");
		assertThat(TicketErrorCode.TICKET_INVALID_GRANT.getStatus()).isEqualTo(400);
		assertThat(TicketErrorCode.TICKET_GRANT_SOURCE_NOT_FOUND.getCode()).isEqualTo("TICKET-002");
		assertThat(TicketErrorCode.TICKET_GRANT_SOURCE_NOT_FOUND.getStatus()).isEqualTo(404);
		assertThat(TicketErrorCode.TICKET_GRANT_SOURCE_MISMATCH.getCode()).isEqualTo("TICKET-003");
		assertThat(TicketErrorCode.TICKET_GRANT_SOURCE_MISMATCH.getStatus()).isEqualTo(409);
	}

	@Test
	@DisplayName("차감·반환 오류의 코드와 HTTP 상태는 TICKET-005부터 009까지 정해진 값이다")
	void useAndRefundCodes() {
		// given
		// when
		// then
		assertThat(TicketErrorCode.TICKET_INVALID_USE.getCode()).isEqualTo("TICKET-005");
		assertThat(TicketErrorCode.TICKET_INVALID_USE.getStatus()).isEqualTo(400);
		assertThat(TicketErrorCode.TICKET_INSUFFICIENT.getCode()).isEqualTo("TICKET-006");
		assertThat(TicketErrorCode.TICKET_INSUFFICIENT.getStatus()).isEqualTo(409);
		assertThat(TicketErrorCode.TICKET_USE_NOT_FOUND.getCode()).isEqualTo("TICKET-007");
		assertThat(TicketErrorCode.TICKET_USE_NOT_FOUND.getStatus()).isEqualTo(404);
		assertThat(TicketErrorCode.TICKET_USE_MISMATCH.getCode()).isEqualTo("TICKET-008");
		assertThat(TicketErrorCode.TICKET_USE_MISMATCH.getStatus()).isEqualTo(409);
		assertThat(TicketErrorCode.TICKET_REFUND_STATE_MISMATCH.getCode()).isEqualTo("TICKET-009");
		assertThat(TicketErrorCode.TICKET_REFUND_STATE_MISMATCH.getStatus()).isEqualTo(409);
	}
}
