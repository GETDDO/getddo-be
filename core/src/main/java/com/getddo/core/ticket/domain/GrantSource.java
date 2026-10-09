package com.getddo.core.ticket.domain;

import java.util.UUID;

import lombok.Getter;

/**
 * 응모권 지급의 근거가 된 청구 한 건.
 *
 * <p>호출자가 먼저 저장한 청구 행(미션·출석·게임 보상 청구)의 종류와 ID를 가리킨다.
 * 같은 청구는 한 번만 지급되며, 그 판단은 이 청구로 이미 발급된 응모권이 있는지로 한다.</p>
 */
@Getter
public final class GrantSource {

	private final GrantSourceType type;
	private final UUID claimId;

	public GrantSource(GrantSourceType type, UUID claimId) {
		this.type = type;
		this.claimId = claimId;
	}
}
