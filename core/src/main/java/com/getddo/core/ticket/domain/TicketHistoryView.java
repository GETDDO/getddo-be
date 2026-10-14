package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 사용자에게 보여 주는 응모권 처리 이력 한 건(T02). 응모권 한 장의 처리 한 번이다.
 *
 * <p>이벤트·미션·게임·출석일은 응모권이 가리키는 지급 청구와 이력이 가리키는 응모 기록에서 가져온 파생 값이며
 * 해당 처리와 관계없으면 null이다. 등급은 지급 후 바뀌지 않으므로 응모권의 등급이다.</p>
 */
@Getter
public final class TicketHistoryView {

	private final UUID id;
	private final UUID ticketId;
	private final TicketOperationType operationType;
	private final TicketGrade grade;
	/** 처리 직후의 상태. */
	private final TicketStatus status;
	/** 처리 직후의 만료 시각. */
	private final Instant expiresAt;
	private final String reason;
	private final Instant createdAt;
	private final UUID eventId;
	private final UUID eventEntryId;
	private final UUID missionId;
	private final UUID gameId;
	/** 출석 보상이면 출석한 KST 날짜. */
	private final LocalDate attendanceDate;
	private final UUID originalUseHistoryId;
	private final UUID correctedHistoryId;

	public TicketHistoryView(UUID id, UUID ticketId, TicketOperationType operationType, TicketGrade grade,
			TicketStatus status, Instant expiresAt, String reason, Instant createdAt, UUID eventId,
			UUID eventEntryId, UUID missionId, UUID gameId, LocalDate attendanceDate, UUID originalUseHistoryId,
			UUID correctedHistoryId) {
		this.id = Objects.requireNonNull(id, "id");
		this.ticketId = Objects.requireNonNull(ticketId, "ticketId");
		this.operationType = Objects.requireNonNull(operationType, "operationType");
		this.grade = Objects.requireNonNull(grade, "grade");
		this.status = Objects.requireNonNull(status, "status");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
		this.reason = Objects.requireNonNull(reason, "reason");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.eventId = eventId;
		this.eventEntryId = eventEntryId;
		this.missionId = missionId;
		this.gameId = gameId;
		this.attendanceDate = attendanceDate;
		this.originalUseHistoryId = originalUseHistoryId;
		this.correctedHistoryId = correctedHistoryId;
	}

	/** 이 이력 다음부터 조회하는 커서. 정렬 기준인 처리 시각과 ID로 만든다. */
	public TicketHistoryCursor cursor() {
		return new TicketHistoryCursor(createdAt, id);
	}
}
