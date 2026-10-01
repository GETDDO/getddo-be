package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 내 응모권 지갑 조회 결과(T01). 사용 가능 잔액과 지갑 상태는 모두 같은 {@code serverTime} 기준이다. */
@Getter
@EqualsAndHashCode
public final class MyTicketWallets {

	private final long availableBalance;
	/** 만료된 지갑을 포함한 전체 지갑. 만료월 최신순이다. */
	private final List<TicketWalletView> wallets;
	private final Instant serverTime;

	private MyTicketWallets(long availableBalance, List<TicketWalletView> wallets, Instant serverTime) {
		this.availableBalance = availableBalance;
		this.wallets = List.copyOf(wallets);
		this.serverTime = serverTime;
	}

	/** 조회 시각 기준 지갑 목록으로 결과를 만든다. 사용 가능 잔액은 만료되지 않은 지갑 잔액의 합이다. */
	public static MyTicketWallets of(List<TicketWalletView> wallets, Instant serverTime) {
		long available = 0;
		for (TicketWalletView wallet : wallets) {
			available = Math.addExact(available, wallet.availableBalance());
		}
		return new MyTicketWallets(available, wallets, serverTime);
	}
}
