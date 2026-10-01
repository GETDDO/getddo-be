package com.getddo.core.ticket.domain;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 응모권 이력 조회 조건(T02). 모든 조건은 선택이며 기간은 {@code [from, to)}로 적용한다. */
@Getter
@EqualsAndHashCode
@ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class TicketLedgerFilter {

	private static final TicketLedgerFilter NONE = new TicketLedgerFilter(null, null, null);

	/** 전체면 null. */
	private final TicketTransactionType transactionType;
	/** 시작 시각(포함). 제한 없으면 null. */
	private final Instant from;
	/** 끝 시각(제외). 제한 없으면 null. */
	private final Instant to;

	/** 조회 조건을 만든다. 조건 사이의 모순은 조회 서비스가 검증한다. */
	public static TicketLedgerFilter of(TicketTransactionType transactionType, Instant from, Instant to) {
		return new TicketLedgerFilter(transactionType, from, to);
	}

	public static TicketLedgerFilter none() {
		return NONE;
	}

	/** 시작과 끝이 모두 있으면 시작이 끝보다 앞서는지 확인한다. */
	public boolean hasValidPeriod() {
		return from == null || to == null || from.isBefore(to);
	}
}
