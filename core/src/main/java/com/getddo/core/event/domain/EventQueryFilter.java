package com.getddo.core.event.domain;

import java.time.Instant;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 목록의 선택 조건. 기간은 모집 기간과 검색 구간의 겹침 여부로 판정한다. */
@Getter
@EqualsAndHashCode
public final class EventQueryFilter {
	private final EventStatus status;
	private final EventType eventType;
	private final MembershipRule membershipRule;
	private final String keyword;
	/** 포함하는 UTC 하한. 관리자 조회 서비스가 KST 시작 날짜를 자정으로 변환한다. */
	private final Instant from;
	/** 제외하는 UTC 상한. 관리자 조회 서비스가 KST 종료 날짜 다음 날 자정을 변환한다. */
	private final Instant to;

	public EventQueryFilter(EventStatus status, EventType eventType, MembershipRule membershipRule,
			String keyword, Instant from, Instant to) {
		this.status = status;
		this.eventType = eventType;
		this.membershipRule = membershipRule;
		this.keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
		this.from = from;
		this.to = to;
	}
}
