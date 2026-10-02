package com.getddo.core.event.domain;

import java.time.Instant;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 불변 이벤트 조회 결과와 관리자용 운영 메타데이터. */
@Getter
@RequiredArgsConstructor
@EqualsAndHashCode
public final class EventView {
	private final RegisteredEvent details;
	private final EventStatus publicStatus;
	private final EventStatus suspendedFromStatus;
	private final Instant suspendedAt;
	private final Instant canceledAt;
}
