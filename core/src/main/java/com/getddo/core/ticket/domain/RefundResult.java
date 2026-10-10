package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.List;

import lombok.Getter;

/** 확정된 응모권 반환 결과. */
@Getter
public final class RefundResult {

	private final List<UsedTicket> tickets;
	/** 반환 시각 UTC. 반환 이력의 처리 시각과 같다. */
	private final Instant refundedAt;
	/** 반환 후 만료 시각 UTC. 한 반환 건의 응모권은 모두 같다. */
	private final Instant expiresAt;
	/** true면 이번 호출이 아니라 이전에 확정된 반환을 돌려준 것이다. */
	private final boolean replayed;

	public RefundResult(List<UsedTicket> tickets, Instant refundedAt, Instant expiresAt, boolean replayed) {
		this.tickets = List.copyOf(tickets);
		this.refundedAt = refundedAt;
		this.expiresAt = expiresAt;
		this.replayed = replayed;
	}

	public long getQuantity() {
		return tickets.size();
	}
}
