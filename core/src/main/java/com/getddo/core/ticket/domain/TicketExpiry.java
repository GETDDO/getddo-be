package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.Objects;

import com.getddo.core.common.time.TimeProvider;

/** 응모권 만료 시각 계산. 업무월은 KST 기준이고 결과는 UTC 순간이다. */
public final class TicketExpiry {

	private TicketExpiry() {
	}

	/**
	 * 일반 지급 응모권의 만료 시각을 계산한다.
	 *
	 * <p>지급한 KST 기준월까지 유효하고 다음 달 1일 00:00 KST에 만료한다.
	 * 예: 9월 지급이면 {@code 2026-09-30T15:00:00Z}.</p>
	 */
	public static Instant forGrant(Instant grantedAt, TimeProvider time) {
		Objects.requireNonNull(grantedAt, "grantedAt");
		return time.toUtc(time.businessMonth(grantedAt).plusMonths(1).atDay(1).atStartOfDay());
	}

	/**
	 * 반환된 응모권의 만료 시각을 계산한다.
	 *
	 * <p>반환한 KST 기준월의 다음 달 말까지 유효하고, 다다음 달 1일 00:00 KST에 만료한다.
	 * 예: 9월 반환이면 {@code 2026-10-31T15:00:00Z}.</p>
	 */
	public static Instant forRefund(Instant refundedAt, TimeProvider time) {
		Objects.requireNonNull(refundedAt, "refundedAt");
		return time.toUtc(time.businessMonth(refundedAt).plusMonths(2).atDay(1).atStartOfDay());
	}
}
