package com.getddo.core.ticket.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * 지급 검증에 필요한 청구 행의 값.
 *
 * <p>청구 테이블은 각 도메인이 소유하며 응모권은 ID로 읽기만 한다.</p>
 */
public final class GrantSourceClaim {

	private final UUID userId;
	private final long ticketCount;

	/**
	 * @param userId      청구 행의 {@code user_id}
	 * @param ticketCount 청구 행의 {@code ticket_count}
	 */
	public GrantSourceClaim(UUID userId, long ticketCount) {
		this.userId = userId;
		this.ticketCount = ticketCount;
	}

	public UUID getUserId() {
		return userId;
	}

	public long getTicketCount() {
		return ticketCount;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof GrantSourceClaim that)) {
			return false;
		}
		return ticketCount == that.ticketCount && Objects.equals(userId, that.userId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(userId, ticketCount);
	}

	@Override
	public String toString() {
		return "GrantSourceClaim[userId=" + userId + ", ticketCount=" + ticketCount + "]";
	}
}
