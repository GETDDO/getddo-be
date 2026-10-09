package com.getddo.api.ticket.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketHistoryView;
import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.core.ticket.domain.TicketStatus;

/**
 * T02 응모권 처리 이력 한 건의 공개 필드다. 응모권 한 장의 처리 한 번이다.
 *
 * @param id 이력 ID
 * @param ticketId 응모권 ID
 * @param operationType 처리 유형
 * @param grade 응모권 등급
 * @param status 처리 직후의 상태
 * @param expiresAt 처리 직후의 만료 시각
 * @param reason 처리 사유
 * @param createdAt 처리 시각
 * @param eventId 관련 이벤트 ID. 없으면 null
 * @param eventEntryId 관련 응모 ID. 없으면 null
 * @param missionId 관련 미션 ID. 없으면 null
 * @param gameId 관련 게임 ID. 없으면 null
 * @param attendanceDate 출석 보상이면 출석한 KST 날짜. 아니면 null
 * @param originalUseHistoryId 반환이 되돌리는 원본 사용 이력 ID. 반환이 아니면 null
 * @param correctedHistoryId 정정 대상 이력 ID. 정정이 아니면 null
 */
public record TicketHistoryResponse(UUID id, UUID ticketId, TicketOperationType operationType, TicketGrade grade,
		TicketStatus status, Instant expiresAt, String reason, Instant createdAt, UUID eventId, UUID eventEntryId,
		UUID missionId, UUID gameId, LocalDate attendanceDate, UUID originalUseHistoryId,
		UUID correctedHistoryId) {

	/**
	 * 이력 한 건을 이력 응답으로 옮긴다.
	 *
	 * @param view 서비스가 반환한 이력
	 * @return 공개 필드만 담은 응답 DTO
	 */
	public static TicketHistoryResponse from(TicketHistoryView view) {
		return new TicketHistoryResponse(view.getId(), view.getTicketId(), view.getOperationType(), view.getGrade(),
				view.getStatus(), view.getExpiresAt(), view.getReason(), view.getCreatedAt(), view.getEventId(),
				view.getEventEntryId(), view.getMissionId(), view.getGameId(), view.getAttendanceDate(),
				view.getOriginalUseHistoryId(), view.getCorrectedHistoryId());
	}
}
