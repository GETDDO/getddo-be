package com.getddo.core.ticket.domain;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 지급 검증에 필요한 청구 행의 사용자와 수량. 청구 테이블은 각 도메인이 소유하며 응모권은 ID로 읽기만 한다. */
@Getter
@EqualsAndHashCode
@ToString
@AllArgsConstructor
public final class GrantSourceClaim {

	private final UUID userId;
	private final long ticketCount;
}
