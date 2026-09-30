package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

import com.getddo.core.common.time.TimeProvider;

/**
 * 지갑을 구분하는 만료 묶음. 지갑 키 {@code (user_id, expiry_month)}의 월과 그 만료 시각이다.
 *
 * @param expiryMonth 사용 가능한 마지막 KST 월의 1일. 지급월이 아니라 만료 묶음 구분값이다
 * @param expiresAt   만료 시각 UTC. {@code expiryMonth} 다음 달 1일 00:00 KST
 */
public record TicketWalletPeriod(LocalDate expiryMonth, Instant expiresAt) {

	/**
	 * 일반 지급분이 들어갈 만료 묶음을 계산한다.
	 *
	 * <p>일반 지급 응모권은 지급한 KST 기준월까지 유효하고 다음 달 1일 00:00 KST에 만료한다.
	 * 예: 9월 지급이면 {@code expiryMonth = 2026-09-01}, {@code expiresAt = 2026-09-30T15:00:00Z}.</p>
	 *
	 * @param grantedAt 지급 시각
	 * @param time      KST 월 경계 계산에 쓰는 시간 정책
	 * @return 지급분의 만료 묶음
	 */
	public static TicketWalletPeriod forGrant(Instant grantedAt, TimeProvider time) {
		Objects.requireNonNull(grantedAt, "grantedAt");
		YearMonth grantMonth = time.businessMonth(grantedAt);
		Instant expiresAt = time.toUtc(grantMonth.plusMonths(1).atDay(1).atStartOfDay());
		return new TicketWalletPeriod(grantMonth.atDay(1), expiresAt);
	}
}
