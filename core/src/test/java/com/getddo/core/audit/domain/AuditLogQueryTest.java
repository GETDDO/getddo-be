package com.getddo.core.audit.domain;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getddo.core.audit.exception.AuditQueryErrorCode;
import com.getddo.core.common.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AuditLogQueryTest {
	@ParameterizedTest
	@CsvSource({"0,20", "-1,20", "1,0", "1,-1", "1,101"})
	@DisplayName("페이지와 크기 조건 위반은 생성 시 AUDIT-001이다")
	void rejectsPage(int page, int size) {
		// given / when / then
		assertInvalid(() -> query(page, size, null, null, null, null));
	}

	@Test
	@DisplayName("문자열은 공백을 정리하고 유니코드60자와 큰 페이지를 허용한다")
	void normalizesAndLimitsText() {
		// given / when / then
		AuditLogQuery result = query(Integer.MAX_VALUE, 100, "  EVENT_CANCEL  ", " \t ", null, null);
		assertThat(result.getAction()).isEqualTo("EVENT_CANCEL");
		assertThat(result.getTargetType()).isNull();
		assertThat(query(1, 20, "😀".repeat(60), null, null, null).getAction()).isEqualTo("😀".repeat(60));
		assertInvalid(() -> query(1, 20, "가".repeat(61), null, null, null));
		assertInvalid(() -> query(1, 20, null, "가".repeat(61), null, null));
	}

	@Test
	@DisplayName("기간 양끝은 선택이고 동시 지정 시 순서와 DB 범위를 검사한다")
	void validatesTimeRange() {
		// given
		Instant now = Instant.parse("2026-10-15T00:00:00Z");
		// when / then
		assertThat(query(1, 20, null, null, now, null).getFrom()).isEqualTo(now);
		assertThat(query(1, 20, null, null, null, now).getTo()).isEqualTo(now);
		assertInvalid(() -> query(1, 20, null, null, now, now));
		assertInvalid(() -> query(1, 20, null, null, now.plusSeconds(1), now));
		assertInvalid(() -> query(1, 20, null, null, Instant.parse("0999-12-31T23:59:59Z"), null));
		assertInvalid(() -> query(1, 20, null, null, null, Instant.parse("+10000-01-01T00:00:00Z")));
	}

	private static AuditLogQuery query(int page, int size, String action, String targetType, Instant from, Instant to) {
		return new AuditLogQuery(page, size, null, action, targetType, null, from, to);
	}

	private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatExceptionOfType(BusinessException.class).isThrownBy(call)
				.satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(AuditQueryErrorCode.INVALID_QUERY));
	}
}
