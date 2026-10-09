package com.getddo.core.event.domain;

import java.time.Instant;
import java.util.Objects;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 불변 이벤트 조회 결과와 관리자용 운영 메타데이터. */
@Getter
@EqualsAndHashCode
public final class EventView {
	private final RegisteredEvent details;
	private final EventStatus publicStatus;
	private final Instant canceledAt;

	public EventView(RegisteredEvent details, EventStatus publicStatus, Instant canceledAt) {
		this.details = Objects.requireNonNull(details, "details");
		this.publicStatus = Objects.requireNonNull(publicStatus, "publicStatus");
		this.canceledAt = canceledAt;
	}
}
