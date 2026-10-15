package com.getddo.api.ticket.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketRefundView;

/**
 * AO08 응모권 반환 이력 한 건의 공개 필드다.
 *
 * @param id 반환 이력 ID
 * @param spendHistoryId 반환이 되돌린 사용 이력 ID
 * @param reasonType 반환 사유 종류. 지금은 이벤트 취소만 있다
 * @param refundedAt 반환 시각
 * @param targetExpiresAt 반환 직후의 만료 시각
 */
public record TicketRefundResponse(UUID id, UUID spendHistoryId, UUID ticketId, TicketGrade grade, UUID eventId,
		String eventTitle, UUID userId, String userName, ReasonType reasonType, Instant refundedAt,
		Instant targetExpiresAt) {

	/** 반환 사유 종류. */
	public enum ReasonType {
		EVENT_CANCELED
	}

	/** 서비스가 반환한 반환 이력을 응답으로 옮긴다. 반환은 이벤트 취소로만 일어난다. */
	public static TicketRefundResponse from(TicketRefundView view) {
		return new TicketRefundResponse(view.getId(), view.getSpendHistoryId(), view.getTicketId(), view.getGrade(),
				view.getEventId(), view.getEventTitle(), view.getUserId(), view.getUserName(),
				ReasonType.EVENT_CANCELED, view.getRefundedAt(), view.getTargetExpiresAt());
	}
}
