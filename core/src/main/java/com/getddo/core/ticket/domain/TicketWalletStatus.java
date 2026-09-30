package com.getddo.core.ticket.domain;

/** 응모권 지갑 상태. {@code ticket_wallets.status} ENUM과 같은 값을 가진다. */
public enum TicketWalletStatus {
	/** 입금·사용할 수 있는 지갑. */
	ACTIVE,
	/** 만료 처리된 지갑. */
	EXPIRED
}
