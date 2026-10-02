package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 응모권 원장 행. 수정·삭제 없이 누적되는 거래 이력이다.
 *
 * <p>현재는 지급(GRANT)에 필요한 값만 담는다. 차감·반환·회수 등에서 쓰는 참조는 해당 기능에서 추가한다.</p>
 */
@Getter
@EqualsAndHashCode
public final class TicketLedger {

	/** 저장 전에는 null. */
	private final UUID id;
	private final UUID walletId;
	private final UUID userId;
	private final TicketTransactionType type;
	/** 증가는 양수, 감소는 음수. */
	private final long quantity;
	private final String idempotencyKey;
	private final String reason;
	private final Instant createdAt;
	private final long balanceAfter;
	private final long walletVersion;
	/** 입금(GRANT·REFUND)분의 만료 시각. 그 외 null. */
	private final Instant expiresAt;
	/** 지급 근거 청구. GRANT가 아니면 null. */
	private final GrantSource grantSource;

	public TicketLedger(UUID id, UUID walletId, UUID userId, TicketTransactionType type, long quantity,
			String idempotencyKey, String reason, Instant createdAt, long balanceAfter, long walletVersion,
			Instant expiresAt, GrantSource grantSource) {
		this.id = id;
		this.walletId = walletId;
		this.userId = userId;
		this.type = type;
		this.quantity = quantity;
		this.idempotencyKey = idempotencyKey;
		this.reason = reason;
		this.createdAt = createdAt;
		this.balanceAfter = balanceAfter;
		this.walletVersion = walletVersion;
		this.expiresAt = expiresAt;
		this.grantSource = grantSource;
	}

	/**
	 * 입금이 반영된 지갑으로 지급 원장 행을 만든다.
	 *
	 * <p>{@code createdAt}은 지갑 월과 만료 시각을 계산한 지급 시각과 같아야 한다.
	 * 그래야 원장의 생성 시각, 적용 만료일, 응답의 지급 시각이 서로 어긋나지 않는다.</p>
	 */
	public static TicketLedger grant(TicketWallet depositedWallet, GrantCommand command, Instant grantedAt) {
		GrantSource source = command.getSource();
		return new TicketLedger(
				null,
				depositedWallet.getId(),
				depositedWallet.getUserId(),
				TicketTransactionType.GRANT,
				command.getQuantity(),
				source.idempotencyKey(),
				command.getReason(),
				grantedAt,
				depositedWallet.getBalance(),
				depositedWallet.getVersion(),
				depositedWallet.getExpiresAt(),
				source);
	}

	public GrantResult toGrantResult(boolean replayed) {
		return new GrantResult(id, walletId, quantity, balanceAfter, createdAt, expiresAt, replayed);
	}
}
