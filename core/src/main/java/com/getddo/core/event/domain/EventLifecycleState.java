package com.getddo.core.event.domain;

import java.time.Instant;
import java.util.Objects;

import lombok.Getter;

/** 시작·마감 상태 전환을 위해 잠금 안에서 읽은 현재 일정이다. */
@Getter
public final class EventLifecycleState {
	private final EventStatus status;
	private final Instant startsAt;
	private final Instant endsAt;

	public EventLifecycleState(EventStatus status, Instant startsAt, Instant endsAt) {
		this.status = Objects.requireNonNull(status, "status");
		this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
		this.endsAt = Objects.requireNonNull(endsAt, "endsAt");
	}
}
