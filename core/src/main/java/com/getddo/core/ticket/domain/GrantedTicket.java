package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;

/** 이미 지급된 응모권 한 장의 지급 당시 값. 같은 보상 건의 재요청에 확정된 결과를 돌려줄 때 쓴다. */
@Getter
public final class GrantedTicket {

	private final UUID ticketId;
	private final TicketGrade grade;
	/** 지급 이력의 처리 시각. */
	private final Instant grantedAt;
	/** 지급 이력의 만료 시각. 이후 반환으로 응모권의 현재 만료 시각이 바뀌어도 지급 당시 값이다. */
	private final Instant expiresAt;

	public GrantedTicket(UUID ticketId, TicketGrade grade, Instant grantedAt, Instant expiresAt) {
		this.ticketId = ticketId;
		this.grade = grade;
		this.grantedAt = grantedAt;
		this.expiresAt = expiresAt;
	}
}
