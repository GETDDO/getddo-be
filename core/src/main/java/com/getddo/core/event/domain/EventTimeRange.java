package com.getddo.core.event.domain;

import java.time.Instant;

/**
 * MySQL DATETIME(6)의 공식 지원 범위로 이벤트 등록 시각과 관리자 검색 경계를 검사한다.
 * 프로젝트의 UTC 저장 기준에 맞춘 범위 검사이며, 시간대 변환이나 현실적인 연도 제한은 수행하지 않는다.
 */
public final class EventTimeRange {
	// MySQL DATETIME(6)의 공식 지원 범위.
	private static final Instant MIN = Instant.parse("1000-01-01T00:00:00Z");
	private static final Instant MAX = Instant.parse("9999-12-31T23:59:59.499999Z");

	private EventTimeRange() {
	}

	/** null을 허용하지 않으며 양끝 시각을 포함한다. */
	public static boolean contains(Instant value) {
		return value != null && !value.isBefore(MIN) && !value.isAfter(MAX);
	}
}
