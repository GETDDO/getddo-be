package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 사용자에게 보여 주는 응모권 이력 한 건(T02).
 *
 * <p>미션·게임·출석일·이벤트는 원장이 가리키는 청구·응모 기록에서 가져온 파생 값이며 해당 거래와 관계없으면 null이다.
 * {@code quantity}는 지급·반환이 양수, 차감·만료·회수가 음수다. {@code balanceAfter}는 해당 지갑의 처리 직후 잔액이다.</p>
 */
@Getter
@EqualsAndHashCode
public final class TicketTransactionView {

	private final UUID id;
	private final UUID walletId;
	private final TicketTransactionType transactionType;
	private final long quantity;
	private final long balanceAfter;
	private final String reason;
	private final Instant createdAt;
	/** 입금(GRANT·REFUND)분의 만료 시각. 그 외 null. */
	private final Instant expiresAt;
	private final UUID eventId;
	private final UUID eventEntryId;
	private final UUID missionId;
	private final UUID gameId;
	/** 출석 보상이면 출석한 KST 날짜. */
	private final LocalDate attendanceDate;
	private final UUID relatedLedgerId;
	private final UUID refundOfId;

	public TicketTransactionView(UUID id, UUID walletId, TicketTransactionType transactionType, long quantity,
			long balanceAfter, String reason, Instant createdAt, Instant expiresAt, UUID eventId, UUID eventEntryId,
			UUID missionId, UUID gameId, LocalDate attendanceDate, UUID relatedLedgerId, UUID refundOfId) {
		this.id = Objects.requireNonNull(id, "id");
		this.walletId = Objects.requireNonNull(walletId, "walletId");
		this.transactionType = Objects.requireNonNull(transactionType, "transactionType");
		this.quantity = quantity;
		this.balanceAfter = balanceAfter;
		this.reason = Objects.requireNonNull(reason, "reason");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.expiresAt = expiresAt;
		this.eventId = eventId;
		this.eventEntryId = eventEntryId;
		this.missionId = missionId;
		this.gameId = gameId;
		this.attendanceDate = attendanceDate;
		this.relatedLedgerId = relatedLedgerId;
		this.refundOfId = refundOfId;
	}

	/** 이 이력 다음부터 조회하는 커서. 정렬 기준인 생성 시각과 ID로 만든다. */
	public TicketLedgerCursor cursor() {
		return new TicketLedgerCursor(createdAt, id);
	}
}
