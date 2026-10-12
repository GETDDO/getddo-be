package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;

/**
 * 확정된 응모권 차감 결과. 응모가 차감 장수와 등급별 장수를 기록하는 데 쓴다.
 */
@Getter
public final class UseResult {

	private final List<UsedTicket> tickets;
	/** 차감 시각 UTC. 사용 이력의 처리 시각과 같다. */
	private final Instant usedAt;
	/** true면 이번 호출이 아니라 이전에 확정된 차감을 돌려준 것이다. */
	private final boolean replayed;

	public UseResult(List<UsedTicket> tickets, Instant usedAt, boolean replayed) {
		this.tickets = List.copyOf(tickets);
		this.usedAt = usedAt;
		this.replayed = replayed;
	}

	public long getQuantity() {
		return tickets.size();
	}

	/** 등급별 차감 장수. 차감하지 않은 등급은 0이다. */
	public Map<TicketGrade, Long> countByGrade() {
		Map<TicketGrade, Long> counts = new EnumMap<>(TicketGrade.class);
		for (TicketGrade grade : TicketGrade.values()) {
			counts.put(grade, 0L);
		}
		tickets.forEach(ticket -> counts.merge(ticket.getGrade(), 1L, Long::sum));
		return counts;
	}
}
