package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 응모권 이력 조회 조건(T02). 모든 조건은 선택이다.
 *
 * <p>기간은 {@code [from, to)}로 적용한다.</p>
 */
public final class TicketLedgerFilter {

	private static final TicketLedgerFilter NONE = new TicketLedgerFilter(null, null, null);

	private final TicketTransactionType transactionType;
	private final Instant from;
	private final Instant to;

	private TicketLedgerFilter(TicketTransactionType transactionType, Instant from, Instant to) {
		this.transactionType = transactionType;
		this.from = from;
		this.to = to;
	}

	/**
	 * 조회 조건을 만든다. 조건 사이의 모순은 조회 서비스가 검증한다.
	 *
	 * @param transactionType 거래 유형. 전체면 null
	 * @param from            시작 시각(포함). 제한 없으면 null
	 * @param to              끝 시각(제외). 제한 없으면 null
	 * @return 조회 조건
	 */
	public static TicketLedgerFilter of(TicketTransactionType transactionType, Instant from, Instant to) {
		return new TicketLedgerFilter(transactionType, from, to);
	}

	/** 조건 없이 전체 이력을 조회한다. */
	public static TicketLedgerFilter none() {
		return NONE;
	}

	/** 기간 조건이 비어 있지 않은지 확인한다. 시작과 끝이 모두 있으면 시작이 끝보다 앞서야 한다. */
	public boolean hasValidPeriod() {
		return from == null || to == null || from.isBefore(to);
	}

	public TicketTransactionType getTransactionType() {
		return transactionType;
	}

	public Instant getFrom() {
		return from;
	}

	public Instant getTo() {
		return to;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof TicketLedgerFilter that)) {
			return false;
		}
		return transactionType == that.transactionType
				&& Objects.equals(from, that.from)
				&& Objects.equals(to, that.to);
	}

	@Override
	public int hashCode() {
		return Objects.hash(transactionType, from, to);
	}

	@Override
	public String toString() {
		return "TicketLedgerFilter[transactionType=" + transactionType + ", from=" + from + ", to=" + to + "]";
	}
}
