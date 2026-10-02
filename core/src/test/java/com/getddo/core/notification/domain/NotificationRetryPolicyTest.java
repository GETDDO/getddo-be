package com.getddo.core.notification.domain;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;

import com.getddo.core.notification.exception.NotificationProcessingErrorCode;
import com.getddo.core.notification.exception.NotificationProcessingException;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRetryPolicyTest {
	private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

	@ParameterizedTest
	@CsvSource({"1,10", "2,30", "3,60"})
	@DisplayName("일시적 오류의 생성·발송 재시도 간격은 10초·30초·60초다")
	void schedulesTemporaryFailures(int attempt, int seconds) {
		// given
		RuntimeException failure = new TransientDataAccessResourceException("private SQL");

		// when / then
		assertThat(NotificationRetryPolicy.nextAttemptAt(attempt, NOW, failure)).isEqualTo(NOW.plusSeconds(seconds));
	}

	@Test
	@DisplayName("4번째 실패·입력 오류·제약 위반은 최종 실패로 처리하며 예외 원문을 저장하지 않는다")
	void stopsExhaustedAndPermanentFailures() {
		// given
		RuntimeException temporary = new DataAccessResourceFailureException("private connection details");
		RuntimeException integrity = new DataIntegrityViolationException("private SQL");
		RuntimeException invalid = new NotificationProcessingException(NotificationProcessingErrorCode.INVALID_JOB);

		// when / then
		assertThat(NotificationRetryPolicy.nextAttemptAt(4, NOW, temporary)).isNull();
		assertThat(NotificationRetryPolicy.nextAttemptAt(1, NOW, integrity)).isNull();
		assertThat(NotificationRetryPolicy.nextAttemptAt(1, NOW, invalid)).isNull();
		assertThat(NotificationRetryPolicy.errorCode(temporary)).isEqualTo("NOTIFICATION-005");
		assertThat(NotificationRetryPolicy.errorCode(integrity)).isEqualTo("NOTIFICATION-006");
		assertThat(NotificationRetryPolicy.errorCode(invalid)).isEqualTo("NOTIFICATION-003");
	}
}
