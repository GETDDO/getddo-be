package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import lombok.Getter;

import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;

/**
 * 사용자의 만료 묶음별 응모권 지갑.
 *
 * <p>잔액은 원장을 통해서만 바뀌며, 원장 행 하나당 지갑 갱신은 한 번이다. 갱신마다 {@code version}을
 * 1 올리고 그 값을 원장의 {@code wallet_version}에 기록한다({@code UNIQUE(wallet_id, wallet_version)}).
 * {@code version}은 JPA 낙관적 잠금이 아니라 이 규칙을 위한 업무 값이며, 동시성은 지갑 행의 비관적 잠금으로 막는다.</p>
 */
@Getter
public final class TicketWallet {

	private final UUID id;
	private final UUID userId;
	/** 사용 가능한 마지막 KST 월의 1일. */
	private final LocalDate expiryMonth;
	/** 사용 가능 시작 시각. 지갑의 첫 입금 시각. */
	private final Instant validFrom;
	private final Instant expiresAt;
	private final long balance;
	/** 저장된 상태. 조회 시각 기준 상태는 {@link TicketWalletView}가 계산한다. */
	private final TicketWalletStatus status;
	private final long version;

	public TicketWallet(UUID id, UUID userId, LocalDate expiryMonth, Instant validFrom, Instant expiresAt, long balance,
			TicketWalletStatus status, long version) {
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
	 * @return 잔액이 {@code quantity}만큼, version이 1 늘어난 지갑
	 * @throws IllegalArgumentException 수량이 1 미만인 경우
	 * @throws TicketException          활성 지갑이 아닌 경우 {@code TICKET_WALLET_EXPIRED}
	 */
	public TicketWallet deposit(long quantity) {
		if (quantity < 1) {
			throw new IllegalArgumentException("입금 수량은 1 이상이어야 한다.");
		}
		if (status != TicketWalletStatus.ACTIVE) {
			throw new TicketException(TicketErrorCode.TICKET_WALLET_EXPIRED);
		}
		return new TicketWallet(id, userId, expiryMonth, validFrom, expiresAt,
				Math.addExact(balance, quantity), status, version + 1);
	}
}
