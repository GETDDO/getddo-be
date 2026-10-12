package com.getddo.core.event.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 등록·조회에서 사용하는 저장된 이벤트와 경품 정보. 최초 발표 예정 시각도 이 모델에서 계산한다. */
@Getter
@EqualsAndHashCode
public final class RegisteredEvent {
	private static final Duration PUBLICATION_DELAY = Duration.ofMinutes(5);

	private final UUID id;
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

	/** 정상 최초 발표의 예정 시각이며, 실제 발표 완료 시각이나 재추첨 발표 시각을 뜻하지 않는다. */
	public Instant getPublicationScheduledAt() {
		return endsAt.plus(PUBLICATION_DELAY);
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
