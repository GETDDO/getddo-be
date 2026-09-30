package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 응모권 원장 행. 수정·삭제 없이 누적되는 거래 이력이다.
 *
 * <p>현재는 지급(GRANT)에 필요한 값만 담는다. 차감·반환·회수 등에서 쓰는 참조는 해당 기능에서 추가한다.</p>
 */
public final class TicketLedger {

	private final UUID id;
	private final UUID walletId;
	private final UUID userId;
	private final TicketTransactionType type;
	private final long quantity;
	private final String idempotencyKey;
	private final String reason;
	private final Instant createdAt;
	private final long balanceAfter;
	private final long walletVersion;
	private final Instant expiresAt;
	private final GrantSource grantSource;

	/**
	 * @param id             원장 ID. 저장 전에는 null
	 * @param walletId       거래가 반영된 지갑 ID
	 * @param userId         지갑 소유 사용자 ID
	 * @param type           거래 유형
	 * @param quantity       수량 변동. 증가는 양수, 감소는 음수
	 * @param idempotencyKey 원장 행별 고정 키
	 * @param reason         처리 사유
	 * @param createdAt      거래 시각 UTC
	 * @param balanceAfter   거래 직후 지갑 잔액
	 * @param walletVersion  거래로 갱신된 지갑 version
	 * @param expiresAt      입금(GRANT·REFUND)분의 만료 시각. 그 외 null
	 * @param grantSource    지급 근거 청구. GRANT가 아니면 null
	 */
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
	 *
	 * @param depositedWallet 이번 지급을 반영한 뒤의 지갑
	 * @param command         지급 요청
	 * @param grantedAt       지급 시각
	 * @return 저장 전 지급 원장 행
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

	/**
	 * 이 지급 원장 행을 호출자에게 돌려줄 결과로 바꾼다.
	 *
	 * @param replayed 이전에 확정된 지급을 돌려주는 경우 true
	 * @return 지급 결과
	 */
	public GrantResult toGrantResult(boolean replayed) {
		return new GrantResult(id, walletId, quantity, balanceAfter, createdAt, expiresAt, replayed);
	}

	public UUID getId() {
		return id;
	}

	public UUID getWalletId() {
		return walletId;
	}

	public UUID getUserId() {
		return userId;
	}

	public TicketTransactionType getType() {
		return type;
	}

	public long getQuantity() {
		return quantity;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getReason() {
		return reason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public long getBalanceAfter() {
		return balanceAfter;
	}

	public long getWalletVersion() {
		return walletVersion;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public GrantSource getGrantSource() {
		return grantSource;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof TicketLedger that)) {
			return false;
		}
		return quantity == that.quantity
				&& balanceAfter == that.balanceAfter
				&& walletVersion == that.walletVersion
				&& Objects.equals(id, that.id)
				&& Objects.equals(walletId, that.walletId)
				&& Objects.equals(userId, that.userId)
				&& type == that.type
				&& Objects.equals(idempotencyKey, that.idempotencyKey)
				&& Objects.equals(reason, that.reason)
				&& Objects.equals(createdAt, that.createdAt)
				&& Objects.equals(expiresAt, that.expiresAt)
				&& Objects.equals(grantSource, that.grantSource);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, walletId, userId, type, quantity, idempotencyKey, reason, createdAt, balanceAfter,
				walletVersion, expiresAt, grantSource);
	}

	@Override
	public String toString() {
		return "TicketLedger[id=" + id + ", walletId=" + walletId + ", type=" + type + ", quantity=" + quantity
				+ ", balanceAfter=" + balanceAfter + ", walletVersion=" + walletVersion + "]";
	}
}
