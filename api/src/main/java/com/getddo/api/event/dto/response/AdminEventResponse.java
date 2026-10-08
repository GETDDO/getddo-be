package com.getddo.api.event.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.event.domain.MembershipRule;

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
		List<Prize> prizes,
		String imageKey,
		Instant createdAt,
		Instant updatedAt,
		EventStatus suspendedFromStatus,
		Instant suspendedAt,
		Instant canceledAt,
		List<PrizeImage> prizeImages
) {
	public record Prize(UUID id, int rank, String name, String description, String imageUrl, int winnerCount) {
	}

	public record PrizeImage(UUID prizeId, String imageKey) {
	}
}
