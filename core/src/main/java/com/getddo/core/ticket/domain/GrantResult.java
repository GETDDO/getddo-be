package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 확정된 응모권 지급 결과.
 *
 * <p>{@code quantity}·{@code grantedAt}·{@code expiresAt}은 API 응답의 {@code RewardReceipt}
 * ({@code ticketCount}, {@code grantedAt}, {@code expiresAt})에 그대로 매핑된다.</p>
 */
public final class GrantResult {

	private final UUID ledgerId;
	private final UUID walletId;
	private final long quantity;
	private final long balanceAfter;
	private final Instant grantedAt;
	private final Instant expiresAt;
	private final boolean replayed;

	/**
	 * @param ledgerId     지급 원장 행 ID
	 * @param walletId     입금된 지갑 ID
	 * @param quantity     지급 수량
	 * @param balanceAfter 해당 지갑의 지급 직후 잔액. 사용자 전체 보유량이 아니다
	 * @param grantedAt    지급 시각 UTC. 원장 행의 생성 시각과 같다
	 * @param expiresAt    지급분의 만료 시각 UTC
	 * @param replayed     true면 이번 호출이 아니라 이전에 확정된 지급을 돌려준 것이다
	 */
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

	public UUID getLedgerId() {
		return ledgerId;
	}

	public UUID getWalletId() {
		return walletId;
	}

	public long getQuantity() {
		return quantity;
	}

	public long getBalanceAfter() {
		return balanceAfter;
	}

	public Instant getGrantedAt() {
		return grantedAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public boolean isReplayed() {
		return replayed;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof GrantResult that)) {
			return false;
		}
		return quantity == that.quantity
				&& balanceAfter == that.balanceAfter
				&& replayed == that.replayed
				&& Objects.equals(ledgerId, that.ledgerId)
				&& Objects.equals(walletId, that.walletId)
				&& Objects.equals(grantedAt, that.grantedAt)
				&& Objects.equals(expiresAt, that.expiresAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(ledgerId, walletId, quantity, balanceAfter, grantedAt, expiresAt, replayed);
	}

	@Override
	public String toString() {
		return "GrantResult[ledgerId=" + ledgerId + ", walletId=" + walletId + ", quantity=" + quantity
				+ ", balanceAfter=" + balanceAfter + ", grantedAt=" + grantedAt + ", expiresAt=" + expiresAt
				+ ", replayed=" + replayed + "]";
	}
}
