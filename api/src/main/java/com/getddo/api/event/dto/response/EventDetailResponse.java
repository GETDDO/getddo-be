package com.getddo.api.event.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.event.domain.EventView;
import com.getddo.core.event.domain.MembershipRule;
import com.getddo.core.event.domain.RegisteredEvent;

/** 사용자 상세 응답. 경품 종류·등수·당첨 인원이며 실제 당첨 결과는 제공하지 않는다. */
public record EventDetailResponse(UUID id, String title, String imageUrl, EventType eventType,
		boolean weightingEnabled, MembershipRule membershipRule, Instant startsAt, Instant endsAt,
		EventStatus status, Instant publicationScheduledAt, Instant serverTime, String description,
		Integer maxTicketsPerUser, List<PrizeResponse> prizes) {
	public static EventDetailResponse from(EventView event, Instant serverTime) {
		RegisteredEvent details = event.getDetails();
		return new EventDetailResponse(details.getId(), details.getTitle(), null, details.getEventType(),
				details.isWeightingEnabled(), details.getMembershipRule(), details.getStartsAt(), details.getEndsAt(),
				event.getPublicStatus(), details.getPublicationScheduledAt(), serverTime,
				details.getDescription(), details.getMaxTicketsPerUser(),
				details.getPrizes().stream().map(PrizeResponse::from).toList());
	}
}
