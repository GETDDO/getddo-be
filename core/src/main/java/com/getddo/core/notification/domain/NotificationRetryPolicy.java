package com.getddo.core.notification.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.notification.exception.NotificationProcessingErrorCode;

/** 사용자 확인을 받은 생성·모의 발송 공통 재시도 기준이다. */
public final class NotificationRetryPolicy {
	public static final int MAX_ATTEMPTS = 4;
	public static final Duration LEASE = Duration.ofSeconds(60);
	private static final List<Duration> DELAYS = List.of(
			Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(60));

	private NotificationRetryPolicy() {
	}

	/** 최초 시도를 포함해 최대 4회 처리한다. null은 자동 재시도 없이 최종 실패라는 뜻이다. */
	public static Instant nextAttemptAt(int attemptCount, Instant now, RuntimeException failure) {
		if (attemptCount < 1 || attemptCount >= MAX_ATTEMPTS || !isTemporary(failure)) {
			return null;
		}
		return now.plus(DELAYS.get(attemptCount - 1));
	}

	/** 개인정보나 SQL·예외 원문을 저장하지 않고 안전한 오류 코드만 남긴다. */
	public static String errorCode(RuntimeException failure) {
		if (failure instanceof BusinessException business) {
			return business.getErrorCode().getCode();
		}
		return (isTemporary(failure) ? NotificationProcessingErrorCode.TEMPORARY_FAILURE
				: NotificationProcessingErrorCode.PROCESSING_FAILED).getCode();
	}

	private static boolean isTemporary(RuntimeException failure) {
		return failure instanceof TransientDataAccessException
				|| failure instanceof RecoverableDataAccessException
				|| failure instanceof DataAccessResourceFailureException
				|| (failure instanceof BusinessException business
						&& business.getErrorCode() == NotificationProcessingErrorCode.TEMPORARY_FAILURE);
	}
}
