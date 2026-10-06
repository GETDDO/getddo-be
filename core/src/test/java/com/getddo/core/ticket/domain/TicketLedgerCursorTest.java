package com.getddo.core.ticket.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketLedgerCursorTest {

	private static final Instant CREATED_AT = Instant.parse("2026-09-15T03:00:00.123456Z");
	private static final UUID ID = UUID.fromString("0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b");

	@Test
	@DisplayName("인코딩한 커서를 다시 해석하면 같은 생성 시각(마이크로초)과 ID가 된다")
	void roundTrips() {
		// given
		TicketLedgerCursor cursor = new TicketLedgerCursor(CREATED_AT, ID);
		// when
		TicketLedgerCursor decoded = TicketLedgerCursor.decode(cursor.encode());
		// then
		assertThat(decoded).usingRecursiveComparison().isEqualTo(cursor);
		assertThat(decoded.getCreatedAt()).isEqualTo(CREATED_AT);
		assertThat(decoded.getId()).isEqualTo(ID);
	}

	@Test
	@DisplayName("커서 문자열은 URL에 그대로 쓸 수 있는 문자만 쓴다")
	void encodesUrlSafe() {
		// given
		TicketLedgerCursor cursor = new TicketLedgerCursor(CREATED_AT, ID);
		// when
		String encoded = cursor.encode();
		// then
		assertThat(encoded).matches("[A-Za-z0-9_-]+");
	}

	@ParameterizedTest
	@ValueSource(strings = {"not base64!", "bm8tc2VwYXJhdG9y", "YWJjfGRlZg"})
	@DisplayName("형식이 올바르지 않은 커서는 TICKET-004로 거절한다")
	void rejectsMalformed(String encoded) {
		// given
		// when
		// then
		assertThatThrownBy(() -> TicketLedgerCursor.decode(encoded))
				.isInstanceOf(TicketException.class)
				.extracting(error -> ((TicketException) error).getErrorCode())
				.isEqualTo(TicketErrorCode.TICKET_INVALID_LEDGER_QUERY);
	}

	@Test
	@DisplayName("시각은 맞지만 ID가 UUID가 아니면 거절한다")
	void rejectsInvalidId() {
		// given
		String encoded = Base64.getUrlEncoder().withoutPadding()
				.encodeToString((CREATED_AT + "|not-a-uuid").getBytes(StandardCharsets.UTF_8));
		// when
		// then
		assertThatThrownBy(() -> TicketLedgerCursor.decode(encoded)).isInstanceOf(TicketException.class);
	}
}
