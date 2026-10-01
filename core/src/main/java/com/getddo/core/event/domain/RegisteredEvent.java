package com.getddo.core.event.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 저장이 완료된 이벤트와 경품의 불변 결과. */
@Getter
@EqualsAndHashCode
public final class RegisteredEvent {
	private final UUID id;
	private final UUID createdBy;
	private final String title;
	private final String description;
	private final String imageKey;
	private final EventType eventType;
	private final boolean weightingEnabled;
	private final Integer maxTicketsPerUser;
	private final MembershipRule membershipRule;
	private final Instant startsAt;
	private final Instant endsAt;
	private final EventStatus status;
	private final Instant createdAt;
	private final Instant updatedAt;
	private final List<Prize> prizes;

	public RegisteredEvent(UUID id,
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
			List<Prize> prizes) {
		this.id = id;
		this.createdBy = createdBy;
		this.title = title;
		this.description = description;
		this.imageKey = imageKey;
		this.eventType = eventType;
		this.weightingEnabled = weightingEnabled;
		this.maxTicketsPerUser = maxTicketsPerUser;
		this.membershipRule = membershipRule;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.status = status;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.prizes = List.copyOf(prizes);
	}

	@Getter
	@RequiredArgsConstructor
	@EqualsAndHashCode
	public static final class Prize {
		private final UUID id;
		private final int rank;
		private final String name;
		private final String description;
		private final String imageKey;
		private final int winnerCount;
	}
}