package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 관리자에게 보여 주는 응모권 반환 이력 한 건(AO08). 반환 이력({@code REFUND}) 한 건이 한 행이다.
 * 반환은 이벤트 취소로만 일어나므로 사유 종류는 응답 단계에서 고정한다.
 */
@Getter
public final class TicketRefundView {

	/** 반환 이력 ID. */
	private final UUID id;
	/** 반환이 되돌린 사용 이력 ID. */
	private final UUID spendHistoryId;
	private final UUID ticketId;
	private final TicketGrade grade;
	private final UUID eventId;
	private final String eventTitle;
	private final UUID userId;
	private final String userName;
	private final Instant refundedAt;
	/** 반환 직후의 만료 시각. */
	private final Instant targetExpiresAt;

	public TicketRefundView(UUID id, UUID spendHistoryId, UUID ticketId, TicketGrade grade, UUID eventId,
			String eventTitle, UUID userId, String userName, Instant refundedAt, Instant targetExpiresAt) {
		this.id = Objects.requireNonNull(id, "id");
		this.spendHistoryId = Objects.requireNonNull(spendHistoryId, "spendHistoryId");
		this.ticketId = Objects.requireNonNull(ticketId, "ticketId");
		this.grade = Objects.requireNonNull(grade, "grade");
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.eventTitle = Objects.requireNonNull(eventTitle, "eventTitle");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.userName = Objects.requireNonNull(userName, "userName");
		this.refundedAt = Objects.requireNonNull(refundedAt, "refundedAt");
		this.targetExpiresAt = Objects.requireNonNull(targetExpiresAt, "targetExpiresAt");
	}
}
