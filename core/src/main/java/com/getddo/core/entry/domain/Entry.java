package com.getddo.core.entry.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 접수 완료된 응모 한 건. 행이 있다는 것이 곧 접수 완료이며, 거절된 시도는 저장하지 않는다.
 *
 * <p>응모 ID는 클라이언트가 응모 요청 전에 정해 보내는 UUID({@code Idempotency-Key})다. 같은 ID로 다시 요청하면 새로
 * 응모하지 않고 이 응모를 돌려준다.</p>
 */
@Getter
public final class Entry {

	private final UUID id;
	private final UUID participantId;
	private final UUID eventId;
	private final UUID userId;
	/** 사용자가 요청한 응모권 장수. 접수된 응모는 요청한 만큼 모두 차감한다. */
	private final int requestedTicketCount;
	private final int deductedTicketCount;
	/** 접수 완료 시각. */
	private final Instant createdAt;

	public Entry(UUID id, UUID participantId, UUID eventId, UUID userId, int requestedTicketCount,
			int deductedTicketCount, Instant createdAt) {
		this.id = Objects.requireNonNull(id, "id");
		this.participantId = Objects.requireNonNull(participantId, "participantId");
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		if (requestedTicketCount < 0 || deductedTicketCount < 0) {
			throw new IllegalArgumentException("응모권 수량은 0 이상이어야 한다.");
		}
		if (deductedTicketCount > requestedTicketCount) {
			throw new IllegalArgumentException("차감 수량은 요청 수량을 넘을 수 없다.");
		}
		this.requestedTicketCount = requestedTicketCount;
		this.deductedTicketCount = deductedTicketCount;
	}

	/** 요청한 만큼 모두 차감한 접수 완료 응모를 만든다. */
	public static Entry accepted(UUID id, EntryParticipant participant, UUID userId, int ticketCount,
			Instant acceptedAt) {
		return new Entry(id, participant.getId(), participant.getEventId(), userId, ticketCount, ticketCount,
				acceptedAt);
	}
}
