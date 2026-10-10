package com.getddo.core.ticket.domain;

import java.util.UUID;

import lombok.Getter;

/** 한 응모에 쓰였거나 반환된 응모권 한 장. */
@Getter
public final class UsedTicket {

	private final UUID ticketId;
	private final TicketGrade grade;

	public UsedTicket(UUID ticketId, TicketGrade grade) {
		this.ticketId = ticketId;
		this.grade = grade;
	}
}
