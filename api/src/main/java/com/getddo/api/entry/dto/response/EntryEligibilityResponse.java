package com.getddo.api.entry.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.getddo.core.entry.domain.EntryEligibility;

/**
 * 응모 자격 사전 조회 결과의 공개 필드다. 응모를 보장하지 않으며 응모 때 다시 검증한다.
 *
 * @param reasons 응모할 수 없는 사유. 응모 오류 이름과 같으며 응모할 수 있으면 비어 있다
 * @param usedTicketCount 이 이벤트에서 누적으로 차감한 응모권 수
 * @param remainingTicketLimit 앞으로 더 쓸 수 있는 장수. {@code null}이면 수량 상한이 없는 이벤트다
 * @param availableTicketBalance 이 이벤트에서 실제로 쓸 수 있는 보유 장수. 가중치를 적용하지 않는 이벤트는 브론즈 장수다
 */
public record EntryEligibilityResponse(
		UUID eventId,
		boolean canEnter,
		List<String> reasons,
		long usedTicketCount,
		Long remainingTicketLimit,
		long availableTicketBalance,
		Instant serverTime) {

	public static EntryEligibilityResponse from(EntryEligibility eligibility) {
		return new EntryEligibilityResponse(eligibility.getEventId(), eligibility.isCanEnter(),
				eligibility.getReasons(), eligibility.getUsedTicketCount(), eligibility.getRemainingTicketLimit(),
				eligibility.getAvailableTicketBalance(), eligibility.getServerTime());
	}
}
