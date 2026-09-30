package com.getddo.core.ticket.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * 응모권 지급의 근거가 된 청구 한 건.
 *
 * <p>호출자가 먼저 저장한 청구 행(미션·출석·게임 보상 청구)의 종류와 ID를 가리킨다.
 * 같은 청구는 한 번만 지급되며, 그 판단 기준이 {@link #idempotencyKey()}다.</p>
 */
public final class GrantSource {

	private static final String GRANT_KEY_PREFIX = "GRANT:";

	private final GrantSourceType type;
	private final UUID claimId;

	/**
	 * @param type    청구 종류
	 * @param claimId 청구 행 ID
	 */
	public GrantSource(GrantSourceType type, UUID claimId) {
		this.type = type;
		this.claimId = claimId;
	}

	/**
	 * 이 청구의 지급 원장 행을 식별하는 멱등키를 만든다.
	 *
	 * <p>형식은 {@code GRANT:{종류}:{청구ID}}다. 지갑 ID를 넣지 않는 이유는 지갑이 지급 시각의
	 * KST 월로 정해지기 때문이다. 월 경계에서 같은 청구를 다시 처리하면 다른 지갑이 선택되어
	 * 키가 달라지고 같은 청구가 두 번 지급될 수 있다. 청구 ID만으로 만든 키는 월과 무관하게 같다.</p>
	 *
	 * @return {@code ticket_ledger.idempotency_key}에 저장하는 값
	 */
	public String idempotencyKey() {
		return GRANT_KEY_PREFIX + type.name() + ":" + claimId;
	}

	public GrantSourceType getType() {
		return type;
	}

	public UUID getClaimId() {
		return claimId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof GrantSource that)) {
			return false;
		}
		return type == that.type && Objects.equals(claimId, that.claimId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, claimId);
	}

	@Override
	public String toString() {
		return "GrantSource[type=" + type + ", claimId=" + claimId + "]";
	}
}
