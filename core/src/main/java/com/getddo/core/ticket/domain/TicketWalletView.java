package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 사용자에게 보여 주는 지갑 한 개의 상태.
 *
 * <p>{@code status}는 저장된 상태가 아니라 조회 시각 기준이다. 만료 처리가 늦어져 아직 ACTIVE로 저장된 지갑도
 * 만료 시각이 지났으면 EXPIRED로 보여 준다.</p>
 */
@Getter
@EqualsAndHashCode
@ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class TicketWalletView {

	private final UUID id;
	/** 사용 가능한 마지막 KST 월의 1일. */
	private final LocalDate expiryMonth;
	private final Instant validFrom;
	private final Instant expiresAt;
	/** 저장된 잔액. 만료된 지갑이면 사용할 수 없는 수량이다. */
	private final long balance;
	private final TicketWalletStatus status;

	/** 저장된 지갑을 조회 시각 기준 상태로 바꾼다. 만료 시각과 조회 시각이 같으면 만료로 본다. */
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
}
