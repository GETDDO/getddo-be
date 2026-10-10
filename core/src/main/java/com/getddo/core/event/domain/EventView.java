package com.getddo.core.event.domain;

import java.time.Instant;
import java.util.Objects;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 실제 상태를 가진 이벤트 정보와 사용자에게 표시할 공개 상태, 관리자용 취소 시각을 묶은 조회 결과. */
@Getter
@EqualsAndHashCode
public final class EventView {
	private final RegisteredEvent details;
	/** 재추첨 중에는 발표 기록 유무에 따라 PUBLISHED 또는 DRAW_CONFIRMED로 표시한다. */
	private final EventStatus publicStatus;
	private final Instant canceledAt;

	public EventView(RegisteredEvent details, EventStatus publicStatus, Instant canceledAt) {
		this.details = Objects.requireNonNull(details, "details");
		this.publicStatus = Objects.requireNonNull(publicStatus, "publicStatus");
		this.canceledAt = canceledAt;
	}
}
