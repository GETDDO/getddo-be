package com.getddo.db.common.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.auditing.DateTimeProvider;

import static org.assertj.core.api.Assertions.assertThat;

class JpaAuditingConfigTest {

	@ParameterizedTest
	@CsvSource({
			"2026-10-01T03:00:00.123456789Z, 2026-10-01T03:00:00.123456Z",
			"2026-09-30T14:59:59.999999600Z, 2026-09-30T14:59:59.999999Z",
			"2026-10-01T03:00:00.123456Z, 2026-10-01T03:00:00.123456Z",
			"2026-10-01T03:00:00Z, 2026-10-01T03:00:00Z"
	})
	@DisplayName("감사 시각은 반올림 없이 마이크로초 정밀도로 제공한다")
	void providesMicrosecondPrecisionWithoutRounding(String clockValue, String expectedValue) {
		// given
		Clock clock = Clock.fixed(Instant.parse(clockValue), ZoneOffset.UTC);
		JpaAuditingConfig config = new JpaAuditingConfig();
		// when
		DateTimeProvider provider = config.jpaAuditingDateTimeProvider(clock);
		// then
		assertThat(provider.getNow()).contains(Instant.parse(expectedValue));
	}
}
