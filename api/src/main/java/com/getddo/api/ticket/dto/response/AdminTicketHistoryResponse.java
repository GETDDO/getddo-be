package com.getddo.api.ticket.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.getddo.core.ticket.domain.AdminTicketHistoryView;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketHistoryView;
import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.core.ticket.domain.TicketStatus;

/**
 * AO05 관리자 응모권 처리 이력 한 건의 공개 필드다. 사용자 이력({@link TicketHistoryResponse})의 모든 필드에
 * 사용자와 이벤트·미션 제목을 더한다.
 *
 * @param userId 응모권 소유 사용자 ID
 * @param userName 응모권 소유 사용자 이름
 * @param eventTitle 관련 이벤트 제목. 이벤트와 관련 없으면 null
 * @param missionTitle 미션 지급으로 만들어진 응모권이면 미션 제목, 아니면 null
 */
public record AdminTicketHistoryResponse(UUID id, UUID ticketId, TicketOperationType operationType, TicketGrade grade,
		TicketStatus status, Instant expiresAt, String reason, Instant createdAt, UUID eventId, UUID eventEntryId,
		UUID missionId, UUID gameId, LocalDate attendanceDate, UUID originalUseHistoryId, UUID correctedHistoryId,
		UUID userId, String userName, String eventTitle, String missionTitle) {

	/** 서비스가 반환한 이력을 응답으로 옮긴다. */
	public static AdminTicketHistoryResponse from(AdminTicketHistoryView view) {
		TicketHistoryView history = view.getHistory();
		return new AdminTicketHistoryResponse(history.getId(), history.getTicketId(), history.getOperationType(),
				history.getGrade(), history.getStatus(), history.getExpiresAt(), history.getReason(),
				history.getCreatedAt(), history.getEventId(), history.getEventEntryId(), history.getMissionId(),
				history.getGameId(), history.getAttendanceDate(), history.getOriginalUseHistoryId(),
				history.getCorrectedHistoryId(), view.getUserId(), view.getUserName(), view.getEventTitle(),
				view.getMissionTitle());
	}
}
