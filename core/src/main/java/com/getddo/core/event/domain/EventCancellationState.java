package com.getddo.core.event.domain;

import java.util.Objects;

import lombok.Getter;

/** 이벤트 잠금 안에서 읽은 취소 처리에 필요한 현재 정보다. */
@Getter
public final class EventCancellationState {
	private final String title;
	private final EventStatus status;

	public EventCancellationState(String title, EventStatus status) {
		this.title = Objects.requireNonNull(title, "title");
		this.status = Objects.requireNonNull(status, "status");
	}
}
