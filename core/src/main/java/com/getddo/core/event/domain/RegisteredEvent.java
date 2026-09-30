package com.getddo.core.event.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 저장이 완료된 이벤트와 경품 등록 결과. */
public record RegisteredEvent(
		UUID id,
		UUID createdBy,
		String title,
		String description,
		String imageKey,
		EventType eventType,
		boolean weightingEnabled,
		Integer maxTicketsPerUser,
		MembershipRule membershipRule,
		Instant startsAt,
		Instant endsAt,
		EventStatus status,
		Instant createdAt,
		Instant updatedAt,
		List<Prize> prizes
) {
	public record Prize(UUID id, int rank, String name, String description, String imageKey, int winnerCount) {
	}
}
