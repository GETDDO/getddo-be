package com.getddo.api.event.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import com.getddo.api.common.exception.CommonErrorCode;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;

/** 이벤트 API의 UUID와 KST 검색 날짜를 해석한다. */
final class EventRequestContext {
	private EventRequestContext() {
	}

	static UUID uuid(String value) {
		try {
			UUID id = UUID.fromString(value);
			if (!id.toString().equalsIgnoreCase(value)) {
				throw new IllegalArgumentException();
			}
			return id;
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.INVALID_FORMAT);
		}
	}

	/** 시작 날짜의 KST 자정을 UTC 검색 경계로 변환한다. */
	static Instant searchFrom(String value, TimeProvider timeProvider) {
		LocalDate date = date(value);
		return date == null ? null : timeProvider.toUtc(date.atStartOfDay());
	}

	/** 종료 날짜 전체를 포함하도록 다음 날 KST 자정을 배타적 경계로 사용한다. */
	static Instant searchTo(String value, TimeProvider timeProvider) {
		LocalDate date = date(value);
		return date == null ? null : timeProvider.toUtc(date.plusDays(1).atStartOfDay());
	}

	private static LocalDate date(String value) {
		if (value == null) {
			return null;
		}
		if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
			throw new BusinessException(CommonErrorCode.INVALID_FORMAT);
		}
		try {
			return LocalDate.parse(value);
		} catch (DateTimeParseException exception) {
			throw new BusinessException(CommonErrorCode.INVALID_FORMAT);
		}
	}
}
