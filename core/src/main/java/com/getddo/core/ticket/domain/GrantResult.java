package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 확정된 응모권 지급 결과.
 *
 * <p>{@code quantity}·{@code grantedAt}·{@code expiresAt}은 API 응답의 {@code RewardReceipt}
 * ({@code ticketCount}, {@code grantedAt}, {@code expiresAt})에 그대로 매핑된다.</p>
 */
@Getter
@EqualsAndHashCode
public final class GrantResult {

	private final UUID ledgerId;
	private final UUID walletId;
	private final long quantity;
	/** 해당 지갑의 지급 직후 잔액. 사용자 전체 보유량이 아니다. */
	private final long balanceAfter;
	/** 지급 시각 UTC. 원장 행의 생성 시각과 같다. */
	private final Instant grantedAt;
	private final Instant expiresAt;
	/** true면 이번 호출이 아니라 이전에 확정된 지급을 돌려준 것이다. */
	private final boolean replayed;

	public GrantResult(UUID ledgerId, UUID walletId, long quantity, long balanceAfter, Instant grantedAt,
			Instant expiresAt, boolean replayed) {
		this.ledgerId = ledgerId;
		this.walletId = walletId;
		this.quantity = quantity;
		this.balanceAfter = balanceAfter;
		this.grantedAt = grantedAt;
		this.expiresAt = expiresAt;
		this.replayed = replayed;
	}
}
