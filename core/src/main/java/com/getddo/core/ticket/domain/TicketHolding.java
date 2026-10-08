package com.getddo.core.ticket.domain;

import java.time.Instant;

import lombok.Getter;

/** 같은 등급·같은 만료 시각의 사용 가능한 응모권 묶음. 보유 조회(T01)의 한 행이다. */
@Getter
public final class TicketHolding {

	private final TicketGrade grade;
	private final Instant expiresAt;
	/** 이 묶음의 실제 장수. */
	private final long count;

	public TicketHolding(TicketGrade grade, Instant expiresAt, long count) {
		this.grade = grade;
		this.expiresAt = expiresAt;
		this.count = count;
	}
}
