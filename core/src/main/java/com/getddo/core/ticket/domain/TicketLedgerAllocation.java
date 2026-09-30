package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 원장 거래가 어느 입금에서 얼마를 움직였는지 기록하는 배분 행.
 *
 * <p>입금 잔여는 같은 {@code sourceCreditLedgerId}의 수량 합계, 최초 지급별 회수 가능량은 같은
 * {@code originalGrantId}의 수량 합계로 계산한다. 그래서 입금(GRANT·REFUND)은 생성 시 자기 자신에 대한
 * 양수 배분 행을 함께 만든다.</p>
 */
public final class TicketLedgerAllocation {

	private final UUID ledgerId;
	private final UUID sourceCreditLedgerId;
	private final UUID originalGrantId;
	private final long quantity;
	private final Instant createdAt;

	/**
	 * @param ledgerId             이번 거래 원장 ID
	 * @param sourceCreditLedgerId 수량이 변동된 입금 원장 ID
	 * @param originalGrantId      추적되는 최초 지급 원장 ID
	 * @param quantity             출처별 수량 변동
	 * @param createdAt            생성 시각 UTC
	 */
	public TicketLedgerAllocation(UUID ledgerId, UUID sourceCreditLedgerId, UUID originalGrantId, long quantity,
			Instant createdAt) {
		this.ledgerId = ledgerId;
		this.sourceCreditLedgerId = sourceCreditLedgerId;
		this.originalGrantId = originalGrantId;
		this.quantity = quantity;
		this.createdAt = createdAt;
	}

	/**
	 * 저장된 지급 원장 행의 자기 입금 배분 행을 만든다.
	 *
	 * <p>세 ID가 모두 이 지급 원장 ID이고, 수량은 지급 수량, 생성 시각은 원장의 생성 시각이다.</p>
	 *
	 * @param savedGrant ID가 발급된 지급 원장 행
	 * @return 자기 입금 배분 행
	 */
	public static TicketLedgerAllocation selfCredit(TicketLedger savedGrant) {
		UUID grantId = Objects.requireNonNull(savedGrant.getId(), "savedGrant.id");
		return new TicketLedgerAllocation(grantId, grantId, grantId, savedGrant.getQuantity(),
				savedGrant.getCreatedAt());
	}

	public UUID getLedgerId() {
		return ledgerId;
	}

	public UUID getSourceCreditLedgerId() {
		return sourceCreditLedgerId;
	}

	public UUID getOriginalGrantId() {
		return originalGrantId;
	}

	public long getQuantity() {
		return quantity;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof TicketLedgerAllocation that)) {
			return false;
		}
		return quantity == that.quantity
				&& Objects.equals(ledgerId, that.ledgerId)
				&& Objects.equals(sourceCreditLedgerId, that.sourceCreditLedgerId)
				&& Objects.equals(originalGrantId, that.originalGrantId)
				&& Objects.equals(createdAt, that.createdAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(ledgerId, sourceCreditLedgerId, originalGrantId, quantity, createdAt);
	}

	@Override
	public String toString() {
		return "TicketLedgerAllocation[ledgerId=" + ledgerId + ", sourceCreditLedgerId=" + sourceCreditLedgerId
				+ ", originalGrantId=" + originalGrantId + ", quantity=" + quantity + "]";
	}
}
