package com.getddo.db.ticket.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import com.getddo.core.ticket.domain.TicketTransactionType;

/**
 * 응모권 원장 Entity. 수정·삭제 없이 누적하므로 모든 컬럼을 갱신 불가로 매핑한다.
 *
 * <p><b>{@code BaseEntity}를 상속하지 않는 이유.</b> 공통 Entity의 {@code @CreatedDate}는 영속화 시점에 시계를
 * 다시 읽어 서비스가 구한 지급 시각을 덮어쓴다. 지급 원장의 {@code created_at}은 지갑 월·만료 시각을 계산한
 * 지급 시각이자 응답의 {@code grantedAt}이어야 하므로 직접 채운다(ADR-0001의 예외, 응모권 Entity에 한정).
 * ID는 공통 Entity와 같은 UUID v7이다.</p>
 *
 * <p>사용자·지갑·청구 참조는 다른 도메인 Entity와의 연관관계 없이 UUID 값으로만 둔다.
 * 현재는 지급에 필요한 컬럼만 매핑하며, 차감·반환·회수에서 쓰는 참조 컬럼은 해당 기능에서 추가한다.</p>
 */
@Getter
@Entity
@Table(name = "ticket_ledger")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketLedgerEntity {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false, length = 16)
	private UUID id;

	@Column(name = "wallet_id", nullable = false, updatable = false, length = 16)
	private UUID walletId;

	@Column(name = "user_id", nullable = false, updatable = false, length = 16)
	private UUID userId;

	@Column(name = "mission_reward_claim_id", updatable = false, length = 16)
	private UUID missionRewardClaimId;

	@Column(name = "attendance_reward_claim_id", updatable = false, length = 16)
	private UUID attendanceRewardClaimId;

	@Column(name = "game_reward_claim_id", updatable = false, length = 16)
	private UUID gameRewardClaimId;

	/** GRANT·REFUND 입금의 만료 시각 UTC. 그 외 null. */
	@Column(name = "expires_at", updatable = false)
	private Instant expiresAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "transaction_type", nullable = false, updatable = false)
	private TicketTransactionType transactionType;

	@Column(name = "quantity", nullable = false, updatable = false)
	private long quantity;

	@Column(name = "idempotency_key", nullable = false, updatable = false, length = 160)
	private String idempotencyKey;

	@Column(name = "reason", nullable = false, updatable = false, columnDefinition = "text")
	private String reason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "balance_after", nullable = false, updatable = false)
	private long balanceAfter;

	@Column(name = "wallet_version", nullable = false, updatable = false)
	private long walletVersion;

	@Builder
	private TicketLedgerEntity(UUID walletId, UUID userId, UUID missionRewardClaimId,
			UUID attendanceRewardClaimId, UUID gameRewardClaimId, Instant expiresAt,
			TicketTransactionType transactionType, long quantity, String idempotencyKey, String reason,
			Instant createdAt, long balanceAfter, long walletVersion) {
		this.walletId = walletId;
		this.userId = userId;
		this.missionRewardClaimId = missionRewardClaimId;
		this.attendanceRewardClaimId = attendanceRewardClaimId;
		this.gameRewardClaimId = gameRewardClaimId;
		this.expiresAt = expiresAt;
		this.transactionType = transactionType;
		this.quantity = quantity;
		this.idempotencyKey = idempotencyKey;
		this.reason = reason;
		this.createdAt = createdAt;
		this.balanceAfter = balanceAfter;
		this.walletVersion = walletVersion;
	}
}
