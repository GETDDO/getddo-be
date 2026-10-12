package com.getddo.core.notification.domain;

import java.time.Instant;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.notification.exception.NotificationAdminErrorCode;

/** 관리자 작업·발송 목록에서 같은 페이지 한도와 기간 규칙을 적용한다. */
final class NotificationAdminQueryValidation {
	private NotificationAdminQueryValidation() {
	}

	/** page 1 이상, size 1~100, from < to를 검증한다. 기간 양 끝은 선택 값이다. */
	static void validate(int page, int size, Instant from, Instant to) {
		if (page < 1 || size < 1 || size > 100 || (from != null && to != null && !from.isBefore(to))) {
			throw new BusinessException(NotificationAdminErrorCode.INVALID_QUERY);
		}
	}
}
