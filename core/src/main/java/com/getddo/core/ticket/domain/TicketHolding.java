package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.Objects;

import lombok.Getter;

/** 같은 등급·같은 만료 시각의 사용 가능한 응모권 묶음. 보유 조회(T01)의 한 행이다. */
@Getter
public final class TicketHolding {

	private final TicketGrade grade;
	private final Instant expiresAt;
	/** 이 묶음의 실제 장수. */
	private final long count;

	public TicketHolding(TicketGrade grade, Instant expiresAt, long count) {
		Objects.requireNonNull(grade, "grade");
		Objects.requireNonNull(expiresAt, "expiresAt");
		if (count < 1) {
			throw new IllegalArgumentException("보유 묶음의 장수는 1 이상이어야 한다.");
		}
		this.grade = grade;
		this.expiresAt = expiresAt;
		this.count = count;
	}
}
