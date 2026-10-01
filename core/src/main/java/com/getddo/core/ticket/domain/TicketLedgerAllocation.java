package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 원장 거래가 어느 입금에서 얼마를 움직였는지 기록하는 배분 행.
 *
 * <p>입금 잔여는 같은 {@code sourceCreditLedgerId}의 수량 합계, 최초 지급별 회수 가능량은 같은
 * {@code originalGrantId}의 수량 합계로 계산한다. 그래서 입금(GRANT·REFUND)은 생성 시 자기 자신에 대한
 * 양수 배분 행을 함께 만든다.</p>
 */
@Getter
@EqualsAndHashCode
@ToString
@AllArgsConstructor
public final class TicketLedgerAllocation {

	/** 이번 거래 원장 ID. */
	private final UUID ledgerId;
	/** 수량이 변동된 입금 원장 ID. */
	private final UUID sourceCreditLedgerId;
	/** 추적되는 최초 지급 원장 ID. */
	private final UUID originalGrantId;
	private final long quantity;
	private final Instant createdAt;

	/** 저장된 지급 원장 행의 자기 입금 배분 행을 만든다. 세 ID가 모두 이 지급이고 수량·생성 시각은 원장과 같다. */
	public static TicketLedgerAllocation selfCredit(TicketLedger savedGrant) {
		UUID grantId = Objects.requireNonNull(savedGrant.getId(), "savedGrant.id");
		return new TicketLedgerAllocation(grantId, grantId, grantId, savedGrant.getQuantity(),
				savedGrant.getCreatedAt());
	}
}
