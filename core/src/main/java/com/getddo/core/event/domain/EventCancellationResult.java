package com.getddo.core.event.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** 취소와 응모권 반환이 함께 처리된 결과다. 커밋은 호출한 서비스의 트랜잭션 종료 시 확정된다. */
@Getter
public final class EventCancellationResult {
	private final UUID eventId;
	private final EventStatus previousStatus;
	private final long refundedTicketCount;
	private final Instant processedAt;

	public EventCancellationResult(UUID eventId, EventStatus previousStatus, long refundedTicketCount, Instant processedAt) {
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.previousStatus = Objects.requireNonNull(previousStatus, "previousStatus");
		if (refundedTicketCount < 0) {
			throw new IllegalArgumentException("반환 수량은 음수일 수 없습니다.");
		}
		this.refundedTicketCount = refundedTicketCount;
		this.processedAt = Objects.requireNonNull(processedAt, "processedAt");
	}
}
