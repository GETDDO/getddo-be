package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * 사용자에게 보여 주는 지갑 한 개의 상태.
 *
 * <p>{@code status}는 저장된 상태가 아니라 조회 시각 기준이다. 만료 처리가 늦어져 아직 ACTIVE로 저장된 지갑도
 * 만료 시각이 지났으면 EXPIRED로 보여 준다.</p>
 */
public final class TicketWalletView {

	private final UUID id;
	private final LocalDate expiryMonth;
	private final Instant validFrom;
	private final Instant expiresAt;
	private final long balance;
	private final TicketWalletStatus status;

	private TicketWalletView(UUID id, LocalDate expiryMonth, Instant validFrom, Instant expiresAt, long balance,
			TicketWalletStatus status) {
		this.id = id;
		this.expiryMonth = expiryMonth;
		this.validFrom = validFrom;
		this.expiresAt = expiresAt;
		this.balance = balance;
		this.status = status;
	}

	/**
	 * 저장된 지갑을 조회 시각 기준으로 보여 줄 상태로 바꾼다.
	 *
	 * @param wallet     저장된 지갑
	 * @param serverTime 조회 시각
	 * @return 조회 시각 기준 상태를 가진 지갑
	 */
	public static TicketWalletView of(TicketWallet wallet, Instant serverTime) {
		boolean expired = wallet.getStatus() == TicketWalletStatus.EXPIRED
				|| !wallet.getExpiresAt().isAfter(serverTime);
		return new TicketWalletView(wallet.getId(), wallet.getExpiryMonth(), wallet.getValidFrom(),
				wallet.getExpiresAt(), wallet.getBalance(),
				expired ? TicketWalletStatus.EXPIRED : TicketWalletStatus.ACTIVE);
	}

	/** 조회 시각에 사용할 수 있는 잔액. 만료된 지갑은 0이다. */
	public long availableBalance() {
		return status == TicketWalletStatus.ACTIVE ? balance : 0;
	}

	public UUID getId() {
		return id;
	}

	/** 사용 가능한 마지막 KST 월의 1일. */
	public LocalDate getExpiryMonth() {
		return expiryMonth;
	}

	public Instant getValidFrom() {
		return validFrom;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	/** 저장된 잔액. 만료된 지갑이면 사용할 수 없는 수량이다. */
	public long getBalance() {
		return balance;
	}

	public TicketWalletStatus getStatus() {
		return status;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof TicketWalletView that)) {
			return false;
		}
		return balance == that.balance
				&& Objects.equals(id, that.id)
				&& Objects.equals(expiryMonth, that.expiryMonth)
				&& Objects.equals(validFrom, that.validFrom)
				&& Objects.equals(expiresAt, that.expiresAt)
				&& status == that.status;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, expiryMonth, validFrom, expiresAt, balance, status);
	}

	@Override
	public String toString() {
		return "TicketWalletView[id=" + id + ", expiryMonth=" + expiryMonth + ", balance=" + balance
				+ ", status=" + status + "]";
	}
}
