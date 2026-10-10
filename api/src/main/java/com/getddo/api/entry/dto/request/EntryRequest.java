package com.getddo.api.entry.dto.request;

import java.util.EnumMap;
import java.util.Map;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.getddo.core.ticket.domain.TicketGrade;

/**
 * 이벤트 응모 요청.
 *
 * <p>응모 ID는 본문이 아니라 {@code Idempotency-Key} 헤더(UUID)로 받는다. 합계 장수는 받지 않고 서버가 등급별 장수에서
 * 계산한다.</p>
 *
 * @param tickets 쓸 응모권의 등급별 장수. 키는 {@code BRONZE}·{@code SILVER}·{@code GOLD}이고 값은 0 이상이다. 없는 등급은
 *        0으로 보며, 응모권을 쓰지 않는 이벤트는 생략하거나 빈 맵으로 보낸다
 */
public record EntryRequest(Map<TicketGrade, @NotNull @Min(0) Integer> tickets) {

	/** 등급별 장수를 서비스 입력으로 옮긴다. 생략됐으면 빈 맵이다. */
	public Map<TicketGrade, Long> toTickets() {
		Map<TicketGrade, Long> converted = new EnumMap<>(TicketGrade.class);
		if (tickets != null) {
			tickets.forEach((grade, count) -> converted.put(grade, count.longValue()));
		}
		return converted;
	}
}
