package com.getddo.core.entry.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Getter;

/**
 * 응모 자격 사전 조회 결과(E07). 응모를 보장하지 않으며 응모 때 다시 검증한다.
 */
@Getter
public final class EntryEligibility {

	private final UUID eventId;
	private final boolean canEnter;
	/** 응모할 수 없는 사유. 응모 오류 이름과 같다. 응모할 수 있으면 비어 있다. */
	private final List<String> reasons;
	/** 이 이벤트에서 사용자가 누적으로 차감한 응모권 수. */
	private final long usedTicketCount;
	/** 앞으로 더 쓸 수 있는 장수. {@code null}이면 수량 상한이 없는 월말 소진용 이벤트다. 보유량과는 별개다. */
	private final Long remainingTicketLimit;
	/** 이 이벤트에서 실제로 쓸 수 있는 보유 장수. 가중치를 적용하지 않는 이벤트는 사용 가능한 브론즈 장수다. */
	private final long availableTicketBalance;
	private final Instant serverTime;

	public EntryEligibility(UUID eventId, List<String> reasons, long usedTicketCount, Long remainingTicketLimit,
			long availableTicketBalance, Instant serverTime) {
		this.eventId = eventId;
		this.reasons = List.copyOf(reasons);
		this.canEnter = reasons.isEmpty();
		this.usedTicketCount = usedTicketCount;
		this.remainingTicketLimit = remainingTicketLimit;
		this.availableTicketBalance = availableTicketBalance;
		this.serverTime = serverTime;
	}
}
