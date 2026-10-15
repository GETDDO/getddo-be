package com.getddo.core.ticket.domain;

import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 현재 상태가 마지막 처리 이력과 어긋난 응모권 한 장(AO04·AO09).
 *
 * <p>응모권의 현재 버전·상태가 마지막 이력의 버전·상태와 하나라도 다르면 어긋난 것이다. 처리 이력이 하나도 없으면 마지막 이력
 * 버전은 0, 마지막 이력 상태는 null이다.</p>
 */
@Getter
public final class TicketInconsistencyView {

	private final UUID ticketId;
	private final UUID userId;
	private final String userName;
	private final TicketGrade grade;
	private final long ticketVersion;
	private final long latestHistoryVersion;
	private final TicketStatus ticketStatus;
	/** 처리 이력이 없으면 null. */
	private final TicketStatus latestHistoryStatus;

	public TicketInconsistencyView(UUID ticketId, UUID userId, String userName, TicketGrade grade, long ticketVersion,
			long latestHistoryVersion, TicketStatus ticketStatus, TicketStatus latestHistoryStatus) {
		this.ticketId = Objects.requireNonNull(ticketId, "ticketId");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.userName = Objects.requireNonNull(userName, "userName");
		this.grade = Objects.requireNonNull(grade, "grade");
		this.ticketVersion = ticketVersion;
		this.latestHistoryVersion = latestHistoryVersion;
		this.ticketStatus = Objects.requireNonNull(ticketStatus, "ticketStatus");
		this.latestHistoryStatus = latestHistoryStatus;
	}
}
