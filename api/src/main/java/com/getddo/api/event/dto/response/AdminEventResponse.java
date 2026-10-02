package com.getddo.api.event.dto.response;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.event.domain.EventView;
import com.getddo.core.event.domain.MembershipRule;
import com.getddo.core.event.domain.RegisteredEvent;

public record AdminEventResponse(
		UUID id,
		String title,
		String imageUrl,
		EventType eventType,
		boolean weightingEnabled,
		MembershipRule membershipRule,
		Instant startsAt,
		Instant endsAt,
		EventStatus status,
		Instant publicationScheduledAt,
		Instant serverTime,
		String description,
		Integer maxTicketsPerUser,
		List<PrizeResponse> prizes,
		String imageKey,
		UUID createdBy,
		Instant createdAt,
		Instant updatedAt,
		EventStatus suspendedFromStatus,
		Instant suspendedAt,
		Instant canceledAt,
		List<PrizeImage> prizeImages
) {
	public static AdminEventResponse from(RegisteredEvent event, Instant serverTime) {
		return from(event, serverTime, null, null, null);
	}

	public static AdminEventResponse from(EventView event, Instant serverTime) {
		return from(event.getDetails(), serverTime, event.getSuspendedFromStatus(),
				event.getSuspendedAt(), event.getCanceledAt());
	}

	private static AdminEventResponse from(RegisteredEvent event, Instant serverTime,
			EventStatus suspendedFromStatus, Instant suspendedAt, Instant canceledAt) {
		return new AdminEventResponse(event.getId(), event.getTitle(), null, event.getEventType(), event.isWeightingEnabled(),
				event.getMembershipRule(), event.getStartsAt(), event.getEndsAt(), event.getStatus(),
				event.getEndsAt().plus(5, ChronoUnit.MINUTES), serverTime,
				event.getDescription(), event.getMaxTicketsPerUser(), event.getPrizes().stream().map(PrizeResponse::from).toList(),
				event.getImageKey(), event.getCreatedBy(), event.getCreatedAt(), event.getUpdatedAt(),
				suspendedFromStatus, suspendedAt, canceledAt, event.getPrizes().stream().map(PrizeImage::from).toList());
	}

	public record PrizeImage(UUID prizeId, String imageKey) {
		public static PrizeImage from(RegisteredEvent.Prize prize) {
			return new PrizeImage(prize.getId(), prize.getImageKey());
		}
	}
}
