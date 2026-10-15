package com.getddo.api.event.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.getddo.core.event.domain.EventCancellationResult;
import com.getddo.core.event.domain.EventStatus;

/** 취소·반환이 모두 성공한 경우에만 반환하는 AE08 응답이다. */
public record EventCancellationResponse(UUID eventId, EventStatus previousStatus, EventStatus status,
		long refundedTicketCount, Instant processedAt) {
	public static EventCancellationResponse from(EventCancellationResult result) {
		return new EventCancellationResponse(result.getEventId(), result.getPreviousStatus(), EventStatus.CANCELED,
				result.getRefundedTicketCount(), result.getProcessedAt());
	}
}
