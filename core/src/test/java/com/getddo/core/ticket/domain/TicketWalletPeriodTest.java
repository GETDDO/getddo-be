package com.getddo.core.ticket.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getddo.core.common.time.TimeProvider;

import static org.assertj.core.api.Assertions.assertThat;

class TicketWalletPeriodTest {

	private final TimeProvider time = new TimeProvider(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

	@ParameterizedTest(name = "{0} 지급 → {1} 묶음, {2} 만료")
	@DisplayName("지급 시각의 KST 월로 만료 묶음을 정하고 다음 달 1일 00:00 KST에 만료한다")
	@CsvSource({
			// 9월 KST 월말 직전
			"2026-09-30T14:59:59.999999Z, 2026-09-01, 2026-09-30T15:00:00Z",
			// 10월 1일 00:00 KST 정각
			"2026-09-30T15:00:00Z, 2026-10-01, 2026-10-31T15:00:00Z",
			// UTC로는 8월이지만 KST로는 9월 1일
			"2026-08-31T15:00:00Z, 2026-09-01, 2026-09-30T15:00:00Z",
			// 12월 지급은 다음 해 1월 1일 00:00 KST에 만료
			"2026-12-31T14:59:59Z, 2026-12-01, 2026-12-31T15:00:00Z",
			"2026-12-31T15:00:00Z, 2027-01-01, 2027-01-31T15:00:00Z"
	})
	void calculatesPeriodFromKstMonth(String grantedAt, String expiryMonth, String expiresAt) {
		// given
		Instant at = Instant.parse(grantedAt);
		// when
		TicketWalletPeriod period = TicketWalletPeriod.forGrant(at, time);
		// then
		assertThat(period.getExpiryMonth()).isEqualTo(LocalDate.parse(expiryMonth));
		assertThat(period.getExpiresAt()).isEqualTo(Instant.parse(expiresAt));
	}
}
