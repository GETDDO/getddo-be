package com.getddo.core.entry.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 이벤트에 응모한 사용자. 사용자는 이벤트마다 한 명의 응모자로 기록되고, 추가 응모는 같은 응모자에 쌓인다.
 */
@Getter
public final class EntryParticipant {

	/** 저장 전에는 null. */
	private final UUID id;
	private final UUID eventId;
	private final UUID userId;
	/** 이 이벤트에서 사용자가 누적으로 차감한 응모권 수. 이벤트 취소·제외로 반환해도 줄이지 않는다. */
	private final long usedTicketCount;
	private final Instant createdAt;

	public EntryParticipant(UUID id, UUID eventId, UUID userId, long usedTicketCount, Instant createdAt) {
		this.id = id;
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		if (usedTicketCount < 0) {
			throw new IllegalArgumentException("누적 차감 수량은 0 이상이어야 한다.");
		}
		this.usedTicketCount = usedTicketCount;
	}

	/** 처음 응모하는 사용자의 새 응모자를 만든다. 누적 차감 수량은 0이다. */
	public static EntryParticipant first(UUID eventId, UUID userId, Instant createdAt) {
		return new EntryParticipant(null, eventId, userId, 0, createdAt);
	}
}
