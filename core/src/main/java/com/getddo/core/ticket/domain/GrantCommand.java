package com.getddo.core.ticket.domain;

import java.util.UUID;

import lombok.Getter;

/**
 * 응모권 지급 요청.
 *
 * <p>지급 시각·만료 시각·지갑·멱등키는 호출자가 넘기지 않고 서비스가 정한다.
 * 값의 유효성은 {@code TicketGrantService.grant}가 검증한다.</p>
 */
@Getter
public final class GrantCommand {

	/** 청구 행의 {@code user_id}와 같아야 한다. */
	private final UUID userId;
	/** 같은 트랜잭션에서 먼저 저장된 지급 근거 청구. */
	private final GrantSource source;
	/** 1 이상이며 청구 행의 {@code ticket_count}와 같아야 한다. */
	private final long quantity;
	/** 이력 화면에 그대로 표시되는 사유(예: 미션 제목). 공백 불가. */
	private final String reason;

	public GrantCommand(UUID userId, GrantSource source, long quantity, String reason) {
		this.userId = userId;
		this.source = source;
		this.quantity = quantity;
		this.reason = reason;
	}
}
