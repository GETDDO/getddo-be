package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.ticket.domain.TicketGrade;

/** 잠긴 이벤트와 접수 완료된 응모의 원본. HTTP DTO나 JPA Entity를 전달하지 않는다. */
public final class DrawSnapshotSource {
	private DrawSnapshotSource() { }

	public record Event(UUID id, EventType type, boolean weightingEnabled, Instant endsAt,
			EventStatus status, boolean deleted, String membershipRule, List<Prize> prizes) {
		public Event { prizes = List.copyOf(prizes); }
	}

	public record Prize(UUID id, int rank, int winnerCount) { }

	public record Participant(UUID id, UUID userId, long usedTicketCount, String membership,
			String role, List<Entry> entries) {
		public Participant { entries = List.copyOf(entries); }
	}

	public record Entry(UUID id, long deductedTicketCount, Instant acceptedAt, List<Use> uses) {
		public Entry { uses = List.copyOf(uses); }
	}

	/** 현재 티켓 상태가 아닌 해당 응모의 USE 이력과 불변 등급이다. */
	public record Use(UUID historyId, UUID ticketId, UUID ownerId, TicketGrade grade) { }
}
