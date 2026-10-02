package com.getddo.db.ticket.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 원장 배분 Entity. 추가만 하며 수정하지 않는다.
 *
 * <p><b>{@code BaseEntity}를 상속하지 않는 이유.</b> {@code created_at}은 원장 행과 같은 지급 시각이어야 하는데,
 * 공통 Entity의 {@code @CreatedDate}는 영속화 시점에 시계를 다시 읽는다. 그래서 {@code created_at}을 직접 채운다
 * (ADR-0001의 예외, 응모권 Entity에 한정). ID는 공통 Entity와 같은 UUID v7이다.</p>
 *
 * <p>같은 거래·입금·최초 지급 조합은 {@code uq_ticket_ledger_allocations_1}로 한 행만 허용한다.</p>
 */
@Getter
@Entity
@Table(name = "ticket_ledger_allocations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketLedgerAllocationEntity {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false, length = 16)
	private UUID id;

	@Column(name = "ledger_id", nullable = false, updatable = false, length = 16)
	private UUID ledgerId;

	@Column(name = "source_credit_ledger_id", nullable = false, updatable = false, length = 16)
	private UUID sourceCreditLedgerId;

	@Column(name = "original_grant_id", nullable = false, updatable = false, length = 16)
	private UUID originalGrantId;

	@Column(name = "quantity", nullable = false, updatable = false)
	private long quantity;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public TicketLedgerAllocationEntity(UUID ledgerId, UUID sourceCreditLedgerId, UUID originalGrantId, long quantity,
			Instant createdAt) {
		this.ledgerId = ledgerId;
		this.sourceCreditLedgerId = sourceCreditLedgerId;
		this.originalGrantId = originalGrantId;
		this.quantity = quantity;
		this.createdAt = createdAt;
	}
}
