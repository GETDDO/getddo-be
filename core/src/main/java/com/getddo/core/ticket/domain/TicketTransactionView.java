package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * 사용자에게 보여 주는 응모권 이력 한 건(T02).
 *
 * <p>미션·게임·출석일·이벤트는 원장이 가리키는 청구·응모 기록에서 가져온 파생 값이며 해당 거래와 관계없으면 null이다.
 * {@code quantity}는 지급·반환이 양수, 차감·만료·회수가 음수다. {@code balanceAfter}는 해당 지갑의 처리 직후 잔액이다.</p>
 */
public final class TicketTransactionView {

	private final UUID id;
	private final UUID walletId;
	private final TicketTransactionType transactionType;
	private final long quantity;
	private final long balanceAfter;
	private final String reason;
	private final Instant createdAt;
	private final Instant expiresAt;
	private final UUID eventId;
	private final UUID eventEntryId;
	private final UUID missionId;
	private final UUID gameId;
	private final LocalDate attendanceDate;
	private final UUID relatedLedgerId;
	private final UUID refundOfId;

	private TicketTransactionView(Builder builder) {
		this.id = Objects.requireNonNull(builder.id, "id");
		this.walletId = Objects.requireNonNull(builder.walletId, "walletId");
		this.transactionType = Objects.requireNonNull(builder.transactionType, "transactionType");
		this.quantity = builder.quantity;
		this.balanceAfter = builder.balanceAfter;
		this.reason = Objects.requireNonNull(builder.reason, "reason");
		this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt");
		this.expiresAt = builder.expiresAt;
		this.eventId = builder.eventId;
		this.eventEntryId = builder.eventEntryId;
		this.missionId = builder.missionId;
		this.gameId = builder.gameId;
		this.attendanceDate = builder.attendanceDate;
		this.relatedLedgerId = builder.relatedLedgerId;
		this.refundOfId = builder.refundOfId;
	}

	public static Builder builder() {
		return new Builder();
	}

	/** 이 이력 다음부터 조회하는 커서. 정렬 기준인 생성 시각과 ID로 만든다. */
	public TicketLedgerCursor cursor() {
		return new TicketLedgerCursor(createdAt, id);
	}

	public UUID getId() {
		return id;
	}

	public UUID getWalletId() {
		return walletId;
	}

	public TicketTransactionType getTransactionType() {
		return transactionType;
	}

	public long getQuantity() {
		return quantity;
	}

	public long getBalanceAfter() {
		return balanceAfter;
	}

	public String getReason() {
		return reason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	/** 입금(GRANT·REFUND)분의 만료 시각. 그 외 null. */
	public Instant getExpiresAt() {
		return expiresAt;
	}

	public UUID getEventId() {
		return eventId;
	}

	public UUID getEventEntryId() {
		return eventEntryId;
	}

	public UUID getMissionId() {
		return missionId;
	}

	public UUID getGameId() {
		return gameId;
	}

	/** 출석 보상이면 출석한 KST 날짜. */
	public LocalDate getAttendanceDate() {
		return attendanceDate;
	}

	public UUID getRelatedLedgerId() {
		return relatedLedgerId;
	}

	public UUID getRefundOfId() {
		return refundOfId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof TicketTransactionView that)) {
			return false;
		}
		return quantity == that.quantity
				&& balanceAfter == that.balanceAfter
				&& id.equals(that.id)
				&& walletId.equals(that.walletId)
				&& transactionType == that.transactionType
				&& reason.equals(that.reason)
				&& createdAt.equals(that.createdAt)
				&& Objects.equals(expiresAt, that.expiresAt)
				&& Objects.equals(eventId, that.eventId)
				&& Objects.equals(eventEntryId, that.eventEntryId)
				&& Objects.equals(missionId, that.missionId)
				&& Objects.equals(gameId, that.gameId)
				&& Objects.equals(attendanceDate, that.attendanceDate)
				&& Objects.equals(relatedLedgerId, that.relatedLedgerId)
				&& Objects.equals(refundOfId, that.refundOfId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, walletId, transactionType, quantity, balanceAfter, reason, createdAt, expiresAt,
				eventId, eventEntryId, missionId, gameId, attendanceDate, relatedLedgerId, refundOfId);
	}

	@Override
	public String toString() {
		return "TicketTransactionView[id=" + id + ", transactionType=" + transactionType + ", quantity=" + quantity
				+ ", balanceAfter=" + balanceAfter + ", createdAt=" + createdAt + "]";
	}

	/** 필드가 많아 인자 순서 실수를 막으려고 이름으로 값을 채운다. */
	public static final class Builder {

		private UUID id;
		private UUID walletId;
		private TicketTransactionType transactionType;
		private long quantity;
		private long balanceAfter;
		private String reason;
		private Instant createdAt;
		private Instant expiresAt;
		private UUID eventId;
		private UUID eventEntryId;
		private UUID missionId;
		private UUID gameId;
		private LocalDate attendanceDate;
		private UUID relatedLedgerId;
		private UUID refundOfId;

		private Builder() {
		}

		public Builder id(UUID id) {
			this.id = id;
			return this;
		}

		public Builder walletId(UUID walletId) {
			this.walletId = walletId;
			return this;
		}

		public Builder transactionType(TicketTransactionType transactionType) {
			this.transactionType = transactionType;
			return this;
		}

		public Builder quantity(long quantity) {
			this.quantity = quantity;
			return this;
		}

		public Builder balanceAfter(long balanceAfter) {
			this.balanceAfter = balanceAfter;
			return this;
		}

		public Builder reason(String reason) {
			this.reason = reason;
			return this;
		}

		public Builder createdAt(Instant createdAt) {
			this.createdAt = createdAt;
			return this;
		}

		public Builder expiresAt(Instant expiresAt) {
			this.expiresAt = expiresAt;
			return this;
		}

		public Builder eventId(UUID eventId) {
			this.eventId = eventId;
			return this;
		}

		public Builder eventEntryId(UUID eventEntryId) {
			this.eventEntryId = eventEntryId;
			return this;
		}

		public Builder missionId(UUID missionId) {
			this.missionId = missionId;
			return this;
		}

		public Builder gameId(UUID gameId) {
			this.gameId = gameId;
			return this;
		}

		public Builder attendanceDate(LocalDate attendanceDate) {
			this.attendanceDate = attendanceDate;
			return this;
		}

		public Builder relatedLedgerId(UUID relatedLedgerId) {
			this.relatedLedgerId = relatedLedgerId;
			return this;
		}

		public Builder refundOfId(UUID refundOfId) {
			this.refundOfId = refundOfId;
			return this;
		}

		public TicketTransactionView build() {
			return new TicketTransactionView(this);
		}
	}
}
