package com.getddo.core.ticket.domain;

import java.util.UUID;

import lombok.Getter;

/** 지급 검증에 필요한 청구 행의 사용자와 수량. 청구 테이블은 각 도메인이 소유하며 응모권은 ID로 읽기만 한다. */
@Getter
public final class GrantSourceClaim {

	private final UUID userId;
	private final long ticketCount;

	public GrantSourceClaim(UUID userId, long ticketCount) {
		this.userId = userId;
		this.ticketCount = ticketCount;
	}
}
