package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * 사용자의 만료 묶음별 응모권 지갑.
 *
 * <p>잔액은 원장을 통해서만 바뀌며, 원장 행 하나당 지갑 갱신은 한 번이다. 갱신마다 {@code version}을
 * 1 올리고 그 값을 원장의 {@code wallet_version}에 기록한다({@code UNIQUE(wallet_id, wallet_version)}).
 * {@code version}은 JPA 낙관적 잠금이 아니라 이 규칙을 위한 업무 값이며, 동시성은 지갑 행의 비관적 잠금으로 막는다.</p>
 *
 * <p>불변 객체다. 입금은 새 지갑을 반환한다.</p>
 */
public final class TicketWallet {

	private final UUID id;
	private final UUID userId;
	private final LocalDate expiryMonth;
	private final Instant validFrom;
	private final Instant expiresAt;
	private final long balance;
	private final TicketWalletStatus status;
	private final long version;

	/**
	 * @param id          지갑 ID
	 * @param userId      소유 사용자 ID
	 * @param expiryMonth 사용 가능한 마지막 KST 월의 1일
	 * @param validFrom   사용 가능 시작 시각. 지갑의 첫 입금 시각
	 * @param expiresAt   만료 시각 UTC
	 * @param balance     현재 잔액
	 * @param status      저장된 지갑 상태
	 * @param version     지갑 갱신 횟수
	 */
	public TicketWallet(UUID id, UUID userId, LocalDate expiryMonth, Instant validFrom, Instant expiresAt,
			long balance, TicketWalletStatus status, long version) {
		this.id = id;
		this.userId = userId;
		this.expiryMonth = expiryMonth;
		this.validFrom = validFrom;
		this.expiresAt = expiresAt;
		this.balance = balance;
		this.status = status;
		this.version = version;
	}

	/**
	 * 입금한 결과의 지갑을 반환한다. 이 객체는 바꾸지 않는다.
	 *
	 * @param quantity 입금 수량. 1 이상
	 * @return 잔액이 {@code quantity}만큼, version이 1 늘어난 지갑
	 * @throws IllegalArgumentException 수량이 1 미만인 경우
	 * @throws IllegalStateException    활성 지갑이 아닌 경우
	 */
	public TicketWallet deposit(long quantity) {
		if (quantity < 1) {
			throw new IllegalArgumentException("입금 수량은 1 이상이어야 한다.");
		}
		if (status != TicketWalletStatus.ACTIVE) {
			throw new IllegalStateException("활성 지갑에만 입금할 수 있다.");
		}
		return new TicketWallet(id, userId, expiryMonth, validFrom, expiresAt,
				Math.addExact(balance, quantity), status, version + 1);
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public LocalDate getExpiryMonth() {
		return expiryMonth;
	}

	public Instant getValidFrom() {
		return validFrom;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public long getBalance() {
		return balance;
	}

	public TicketWalletStatus getStatus() {
		return status;
	}

	public long getVersion() {
		return version;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof TicketWallet that)) {
			return false;
		}
		return balance == that.balance
				&& version == that.version
				&& Objects.equals(id, that.id)
				&& Objects.equals(userId, that.userId)
				&& Objects.equals(expiryMonth, that.expiryMonth)
				&& Objects.equals(validFrom, that.validFrom)
				&& Objects.equals(expiresAt, that.expiresAt)
				&& status == that.status;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, userId, expiryMonth, validFrom, expiresAt, balance, status, version);
	}

	@Override
	public String toString() {
		return "TicketWallet[id=" + id + ", expiryMonth=" + expiryMonth + ", balance=" + balance
				+ ", status=" + status + ", version=" + version + "]";
	}
}
