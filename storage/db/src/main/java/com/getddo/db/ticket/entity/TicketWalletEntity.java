package com.getddo.db.ticket.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.getddo.core.ticket.domain.TicketWalletStatus;

/**
 * 응모권 지갑 Entity.
 *
 * <p><b>{@code BaseUpdatableEntity}를 상속하지 않는 이유.</b> 공통 Entity의 {@code @CreatedDate}는 영속화 시점에
 * 시계를 다시 읽는다. 응모권은 지급 시각의 KST 월로 지갑과 만료 시각을 정하므로, 월 경계 직전에는 생성 시각이
 * 만료 계산 시각이나 응답의 지급 시각과 어긋날 수 있다. 그래서 새 지갑의 {@code created_at}은 서비스가 구한
 * 지급 시각으로 채운다(ADR-0001의 예외, 응모권 Entity에 한정). {@code updated_at}은 공통 Clock 기반
 * Auditing에 맡긴다.</p>
 *
 * <p>지갑 행은 동시 생성 충돌을 트랜잭션 안에서 흡수하려고 네이티브 {@code INSERT ... ON DUPLICATE KEY UPDATE}로만
 * 만든다. 그래서 이 Entity는 persist하지 않고, ID 생성기도 두지 않는다. 잔액은 {@link #applyDeposit}으로만 바꾼다.</p>
 */
@Getter
@Entity
@Table(name = "ticket_wallets")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketWalletEntity {

	@Id
	@Column(name = "id", nullable = false, updatable = false, length = 16)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false, length = 16)
	private UUID userId;

	/** 사용 가능한 마지막 KST 월의 1일. */
	@Column(name = "expiry_month", nullable = false, updatable = false)
	private LocalDate expiryMonth;

	@Column(name = "valid_from", nullable = false, updatable = false)
	private Instant validFrom;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "balance", nullable = false)
	private long balance;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private TicketWalletStatus status;

	/** 원장의 {@code wallet_version}과 짝을 맞추는 갱신 횟수. JPA 낙관적 잠금용 {@code @Version}이 아니다. */
	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * 입금이 반영된 잔액과 version을 기록한다. 잠금을 잡은 뒤에만 호출한다.
	 *
	 * @param balance 갱신된 잔액
	 * @param version 갱신된 version
	 */
	public void applyDeposit(long balance, long version) {
		this.balance = balance;
		this.version = version;
	}
}
