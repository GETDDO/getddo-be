package com.getddo.db.ticket.mapper;

import com.getddo.core.ticket.domain.TicketWallet;
import com.getddo.db.ticket.entity.TicketWalletEntity;

/** 지갑 Entity와 도메인 객체 변환. */
public final class TicketWalletMapper {

	private TicketWalletMapper() {
	}

	public static TicketWallet toDomain(TicketWalletEntity entity) {
		return new TicketWallet(
				entity.getId(),
				entity.getUserId(),
				entity.getExpiryMonth(),
				entity.getValidFrom(),
				entity.getExpiresAt(),
				entity.getBalance(),
				entity.getStatus(),
				entity.getVersion());
	}
}
