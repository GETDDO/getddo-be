package com.getddo.core.ticket.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getddo.core.common.time.TimeProvider;

import static org.assertj.core.api.Assertions.assertThat;

class TicketExpiryTest {

	private final TimeProvider time = new TimeProvider(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

	@ParameterizedTest
	@CsvSource({
			"2026-09-15T03:00:00Z, 2026-09-30T15:00:00Z",
			"2026-09-30T14:59:59Z, 2026-09-30T15:00:00Z",
			"2026-09-30T15:00:00Z, 2026-10-31T15:00:00Z",
			"2026-12-31T14:59:59Z, 2026-12-31T15:00:00Z",
			"2026-12-31T15:00:00Z, 2027-01-31T15:00:00Z"
	})
	@DisplayName("지급한 KST 월의 다음 달 1일 00:00 KST에 만료한다")
	void expiresAtNextMonthStartInKst(String grantedAt, String expected) {
		// given
		// when
		Instant expiresAt = TicketExpiry.forGrant(Instant.parse(grantedAt), time);
		// then
		assertThat(expiresAt).isEqualTo(Instant.parse(expected));
	}
}
