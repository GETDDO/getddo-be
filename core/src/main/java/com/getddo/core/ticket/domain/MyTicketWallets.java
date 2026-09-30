package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 내 응모권 지갑 조회 결과(T01).
 *
 * <p>사용 가능 잔액과 지갑 상태는 모두 같은 {@code serverTime} 기준으로 계산한다.</p>
 */
public final class MyTicketWallets {

	private final long availableBalance;
	private final List<TicketWalletView> wallets;
	private final Instant serverTime;

	private MyTicketWallets(long availableBalance, List<TicketWalletView> wallets, Instant serverTime) {
		this.availableBalance = availableBalance;
		this.wallets = List.copyOf(wallets);
		this.serverTime = serverTime;
	}

	/**
	 * 조회 시각 기준 지갑 목록으로 결과를 만든다. 사용 가능 잔액은 만료되지 않은 지갑 잔액의 합이다.
	 *
	 * @param wallets    조회 시각 기준 지갑 목록
	 * @param serverTime 조회 시각
	 * @return 조회 결과
	 */
	public static MyTicketWallets of(List<TicketWalletView> wallets, Instant serverTime) {
		long available = 0;
		for (TicketWalletView wallet : wallets) {
			available = Math.addExact(available, wallet.availableBalance());
		}
		return new MyTicketWallets(available, wallets, serverTime);
	}

	/** 조회 시각에 사용할 수 있는 응모권 수. */
	public long getAvailableBalance() {
		return availableBalance;
	}

	/** 만료된 지갑을 포함한 전체 지갑. 만료월 최신순이다. */
	public List<TicketWalletView> getWallets() {
		return wallets;
	}

	public Instant getServerTime() {
		return serverTime;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof MyTicketWallets that)) {
			return false;
		}
		return availableBalance == that.availableBalance
				&& wallets.equals(that.wallets)
				&& Objects.equals(serverTime, that.serverTime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(availableBalance, wallets, serverTime);
	}

	@Override
	public String toString() {
		return "MyTicketWallets[availableBalance=" + availableBalance + ", wallets=" + wallets
				+ ", serverTime=" + serverTime + "]";
	}
}
