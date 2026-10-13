package com.getddo.core.entry.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import lombok.Getter;

import com.getddo.core.ticket.domain.TicketGrade;

/**
 * 접수 완료된 응모 영수증. 접수된 응모는 요청한 응모권을 모두 차감하므로 등급별 요청 장수와 차감 장수가 같다.
 */
@Getter
public final class EntryReceipt {

	private final UUID entryId;
	private final UUID eventId;
	private final String eventTitle;
	/** 등급별 차감 장수. 장수가 0인 등급도 키를 가진다. */
	private final Map<TicketGrade, Long> ticketsByGrade;
	private final Instant acceptedAt;
	/** true면 이번 호출이 새 응모를 접수한 것이고, false면 이전에 접수한 응모를 돌려준 것이다. */
	private final boolean created;

	public EntryReceipt(UUID entryId, UUID eventId, String eventTitle, Map<TicketGrade, Long> ticketsByGrade,
			Instant acceptedAt, boolean created) {
		this.entryId = entryId;
		this.eventId = eventId;
		this.eventTitle = eventTitle;
		Map<TicketGrade, Long> complete = new EnumMap<>(TicketGrade.class);
		for (TicketGrade grade : TicketGrade.values()) {
			complete.put(grade, ticketsByGrade.getOrDefault(grade, 0L));
		}
		this.ticketsByGrade = Collections.unmodifiableMap(complete);
		this.acceptedAt = acceptedAt;
		this.created = created;
	}

	/** 등급별 장수의 합계. */
	public long getTicketCount() {
		return ticketsByGrade.values().stream().mapToLong(Long::longValue).sum();
	}
}
