package com.getddo.api.event.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import com.getddo.api.common.exception.CommonErrorCode;
import com.getddo.core.common.exception.BusinessException;

/** 이벤트 API의 UUID·날짜 입력 형식을 검사하고 타입으로 해석한다. */
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

	/** YYYY-MM-DD 형식과 실제 날짜 여부만 확인하며 검색 경계 계산은 서비스에 맡긴다. */
	static LocalDate date(String value) {
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
