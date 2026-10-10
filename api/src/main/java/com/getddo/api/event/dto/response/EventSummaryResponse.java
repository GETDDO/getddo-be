package com.getddo.api.event.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.event.domain.EventView;
import com.getddo.core.event.domain.MembershipRule;
import com.getddo.core.event.domain.RegisteredEvent;

/** 사용자 목록 응답. 공개 상태와 최초 발표 예정 시각을 제공하고 경품·관리용 필드는 제외한다. */
public record EventSummaryResponse(UUID id, String title, String imageUrl, EventType eventType,
		boolean weightingEnabled, MembershipRule membershipRule, Instant startsAt, Instant endsAt,
		EventStatus status, Instant publicationScheduledAt, Instant serverTime) {
	public static EventSummaryResponse from(EventView event, Instant serverTime) {
		RegisteredEvent details = event.getDetails();
		return new EventSummaryResponse(details.getId(), details.getTitle(), null, details.getEventType(),
				details.isWeightingEnabled(), details.getMembershipRule(), details.getStartsAt(), details.getEndsAt(),
				event.getPublicStatus(), details.getPublicationScheduledAt(), serverTime);
	}
}
