package com.getddo.core.event.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 이벤트와 경품을 한 번에 등록하기 위한 업무 입력. */
public record EventRegistration(
		UUID actorId,
		String title,
		String description,
		String imageKey,
		EventType eventType,
		boolean weightingEnabled,
		Integer maxTicketsPerUser,
		MembershipRule membershipRule,
		Instant startsAt,
		Instant endsAt,
		List<Prize> prizes
) {
	public record Prize(int rank, String name, String description, String imageKey, int winnerCount) {
	}
}
