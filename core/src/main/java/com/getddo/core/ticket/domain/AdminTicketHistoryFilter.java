package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;

/**
 * 관리자 응모권 처리 이력 조회 조건(AO05). 모든 조건은 선택이며 서로 AND로 결합한다. 기간은 처리 시각 기준 {@code [from, to)}다.
 * 이벤트는 사용 이력이 가리키는 응모, 반환 이력이면 되돌린 원본 사용 이력의 응모 기준이다.
 */
@Getter
public final class AdminTicketHistoryFilter {

	private final UUID userId;
	private final UUID eventId;
	/** 미션 지급으로 만들어진 응모권의 이력만 좁힌다. */
	private final UUID missionId;
	private final TicketOperationType operationType;
	private final Instant from;
	private final Instant to;

	private AdminTicketHistoryFilter(UUID userId, UUID eventId, UUID missionId, TicketOperationType operationType,
			Instant from, Instant to) {
		this.userId = userId;
		this.eventId = eventId;
		this.missionId = missionId;
		this.operationType = operationType;
		this.from = from;
		this.to = to;
	}

	public static AdminTicketHistoryFilter of(UUID userId, UUID eventId, UUID missionId,
			TicketOperationType operationType, Instant from, Instant to) {
		return new AdminTicketHistoryFilter(userId, eventId, missionId, operationType, from, to);
	}

	public boolean hasValidPeriod() {
		return from == null || to == null || from.isBefore(to);
	}
}
