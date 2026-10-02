package com.getddo.db.ticket.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 원장 배분 Entity. 추가만 하며 수정하지 않는다.
 *
 * <p><b>{@code BaseEntity}를 상속하지 않는 이유.</b> 이 테이블은 별도 ID 없이
 * {@code (ledger_id, source_credit_ledger_id, original_grant_id)} 복합 기본 키를 쓴다. 또 {@code created_at}은
 * 원장 행과 같은 지급 시각이어야 하는데, 공통 Entity의 {@code @CreatedDate}는 영속화 시점에 시계를 다시 읽는다.
 * 그래서 {@code created_at}을 직접 채운다(ADR-0001의 예외, 응모권 Entity에 한정).</p>
 *
 * <p>복합 키는 {@code @IdClass}로 매핑해 입금별·최초 지급별 합계 조회에서 필드를 바로 쓸 수 있게 한다.
 * ID를 직접 채우므로 저장은 {@code merge}가 아니라 {@code persist}로 한다.</p>
 */
@Getter
@Entity
@Table(name = "ticket_ledger_allocations")
@IdClass(TicketLedgerAllocationId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketLedgerAllocationEntity {

	@Id
	@Column(name = "ledger_id", nullable = false, updatable = false, length = 16)
	private UUID ledgerId;

	@Id
	@Column(name = "source_credit_ledger_id", nullable = false, updatable = false, length = 16)
	private UUID sourceCreditLedgerId;

	@Id
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
