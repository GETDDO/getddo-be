package com.getddo.api.ticket.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketGrantView;

/**
 * AO03 응모권 지급 이력 한 건의 공개 필드다. 응모권 한 장의 지급 한 번이다.
 *
 * @param historyId 지급 이력 ID
 * @param ticketId 응모권 ID
 * @param userId 지급받은 사용자 ID
 * @param userName 지급받은 사용자 이름
 * @param grade 응모권 등급
 * @param sourceType 지급 근거 종류
 * @param sourceId 지급 근거 원본 ID. 출석 기록, 미션 제출, 게임 플레이 ID
 * @param missionId 미션 지급이면 미션 ID, 아니면 null
 * @param grantedAt 지급 시각
 * @param expiresAt 지급 시점의 만료 시각
 */
public record TicketGrantResponse(UUID historyId, UUID ticketId, UUID userId, String userName, TicketGrade grade,
		GrantSourceType sourceType, UUID sourceId, UUID missionId, Instant grantedAt, Instant expiresAt) {

	/** 서비스가 반환한 지급 이력을 응답으로 옮긴다. */
	public static TicketGrantResponse from(TicketGrantView view) {
		return new TicketGrantResponse(view.getHistoryId(), view.getTicketId(), view.getUserId(), view.getUserName(),
				view.getGrade(), view.getSourceType(), view.getSourceId(), view.getMissionId(), view.getGrantedAt(),
				view.getExpiresAt());
	}
}
