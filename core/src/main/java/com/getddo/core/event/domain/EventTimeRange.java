package com.getddo.core.event.domain;

import java.time.Instant;

/** 이벤트 등록·조회에서 사용하는 UTC 시각의 저장 가능 범위. */
public final class EventTimeRange {
	// MySQL DATETIME(6)의 공식 지원 범위.
	private static final Instant MIN = Instant.parse("1000-01-01T00:00:00Z");
	private static final Instant MAX = Instant.parse("9999-12-31T23:59:59.499999Z");

	private EventTimeRange() {
	}

	public static boolean contains(Instant value) {
		return value != null && !value.isBefore(MIN) && !value.isAfter(MAX);
	}
}
