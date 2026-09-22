package com.getddo.core.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TimeProviderTest {

	private static final Instant FIXED_NOW =
			Instant.parse("2026-09-30T15:30:00.123456789Z");
	private final TimeProvider time = new TimeProvider(
			Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

	@Test
	void readsInstantFromInjectedClock() {
		assertThat(time.now()).isEqualTo(FIXED_NOW);
	}

	@Test
	void convertsUtcInstantToKstWithoutChangingInstant() {
		ZonedDateTime kst = time.toKst(FIXED_NOW);
		assertThat(kst.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
		assertThat(kst.toLocalDateTime())
				.isEqualTo(LocalDateTime.parse("2026-10-01T00:30:00.123456789"));
		assertThat(kst.toInstant()).isEqualTo(FIXED_NOW);
	}

	@Test
	void interpretsLocalInputAsKstAndPreservesPrecision() {
		LocalDateTime kst = LocalDateTime.parse("2026-10-01T00:30:00.123456789");
		assertThat(time.toUtc(kst)).isEqualTo(FIXED_NOW);
		assertThat(time.toUtc(time.toKst(FIXED_NOW).toLocalDateTime()))
				.isEqualTo(FIXED_NOW);
	}

	@ParameterizedTest
	@CsvSource({
			"2026-09-30T14:59:59.999999999Z, 2026-09-30, 2026-09",
			"2026-09-30T15:00:00Z, 2026-10-01, 2026-10",
			"2026-12-31T15:00:00Z, 2027-01-01, 2027-01",
			"2028-02-28T15:00:00Z, 2028-02-29, 2028-02",
			"2028-02-29T15:00:00Z, 2028-03-01, 2028-03"
	})
	void calculatesBusinessDateAndMonth(String instant, String date, String month) {
		Instant target = Instant.parse(instant);
		assertThat(time.businessDate(target)).isEqualTo(LocalDate.parse(date));
		assertThat(time.businessMonth(target)).isEqualTo(YearMonth.parse(month));
	}

	@Test
	void businessCalculationsUseSeoulRegardlessOfClockZone() {
		TimeProvider other = new TimeProvider(
				Clock.fixed(FIXED_NOW, ZoneId.of("America/New_York")));
		assertThat(other.now()).isEqualTo(FIXED_NOW);
		assertThat(other.toKst(FIXED_NOW).getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
		assertThat(other.businessDate(FIXED_NOW)).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(other.businessMonth(FIXED_NOW)).isEqualTo(YearMonth.of(2026, 10));
	}

	@Test
	void rejectsMissingClockAndTimeInputs() {
		assertThatNullPointerException().isThrownBy(() -> new TimeProvider(null));
		assertThatNullPointerException().isThrownBy(() -> time.toKst(null));
		assertThatNullPointerException().isThrownBy(() -> time.toUtc(null));
		assertThatNullPointerException().isThrownBy(() -> time.businessDate(null));
		assertThatNullPointerException().isThrownBy(() -> time.businessMonth(null));
	}
}
