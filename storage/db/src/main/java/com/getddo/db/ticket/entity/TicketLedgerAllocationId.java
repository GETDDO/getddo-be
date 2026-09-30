package com.getddo.db.ticket.entity;

import java.io.Serializable;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@link TicketLedgerAllocationEntity}의 복합 기본 키. 필드 이름은 Entity의 {@code @Id} 필드와 같아야 한다. */
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TicketLedgerAllocationId implements Serializable {

	private UUID ledgerId;
	private UUID sourceCreditLedgerId;
	private UUID originalGrantId;
}
