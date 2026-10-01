package com.getddo.core.event.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 이벤트와 경품을 한 번에 등록하기 위한 불변 업무 입력. */
@Getter
@EqualsAndHashCode
public final class EventRegistration {
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
	private final List<Prize> prizes;

	public EventRegistration(UUID createdBy,
			String title,
			String description,
			String imageKey,
			EventType eventType,
			boolean weightingEnabled,
			Integer maxTicketsPerUser,
			MembershipRule membershipRule,
			Instant startsAt,
			Instant endsAt,
			List<Prize> prizes) {
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
		// null 목록·원소는 서비스의 업무 검증에서 처리한다. 원본 목록의 변경만 차단한다.
		this.prizes = prizes == null ? null : Collections.unmodifiableList(new ArrayList<>(prizes));
	}

	@Getter
	@RequiredArgsConstructor
	@EqualsAndHashCode
	public static final class Prize {
		private final int rank;
		private final String name;
		private final String description;
		private final String imageKey;
		private final int winnerCount;
	}
}