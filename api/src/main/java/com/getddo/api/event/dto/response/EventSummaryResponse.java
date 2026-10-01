package com.getddo.api.event.dto.response;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.event.domain.EventView;
import com.getddo.core.event.domain.MembershipRule;
import com.getddo.core.event.domain.RegisteredEvent;

/** 사용자 목록 응답. 이미지 키·생성자·운영 메타데이터는 포함하지 않는다. */
public record EventSummaryResponse(UUID id, String title, String imageUrl, EventType eventType,
		boolean weightingEnabled, MembershipRule membershipRule, Instant startsAt, Instant endsAt,
		EventStatus status, Instant publicationScheduledAt, Instant serverTime) {
	public static EventSummaryResponse from(EventView event, Instant serverTime) {
		RegisteredEvent details = event.getDetails();
		return new EventSummaryResponse(details.getId(), details.getTitle(), null, details.getEventType(),
				details.isWeightingEnabled(), details.getMembershipRule(), details.getStartsAt(), details.getEndsAt(),
				event.getPublicStatus(), details.getEndsAt().plus(5, ChronoUnit.MINUTES), serverTime);
	}
}
