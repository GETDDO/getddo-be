package com.getddo.core.event.domain;

public enum EventStatus {
	SCHEDULED,
	OPEN,
	CLOSED,
	DRAW_CONFIRMED,
	PUBLISHED,
	CANCELED,
	REDRAWING,
	NO_ENTRANTS,
	/** 기존 DB 스키마의 상태 매핑용. 신규 이벤트에는 사용하지 않으며 공개 조회 계약은 별도 검토한다. */
	NO_ELIGIBLE_ENTRANTS
}
