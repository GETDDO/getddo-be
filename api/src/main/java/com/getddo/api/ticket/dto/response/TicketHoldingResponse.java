package com.getddo.api.ticket.dto.response;

import java.time.Instant;

import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketHolding;

/**
 * 같은 등급·같은 만료 시각의 사용 가능한 응모권 묶음 한 개의 공개 필드다.
 *
 * @param grade 응모권 등급
 * @param expiresAt 만료 시각
 * @param count 이 묶음의 실제 장수
 */
public record TicketHoldingResponse(TicketGrade grade, Instant expiresAt, long count) {

	/**
	 * 보유 묶음을 응답으로 옮긴다.
	 *
	 * @param holding 서비스가 반환한 묶음
	 * @return 공개 필드만 담은 응답 DTO
	 */
	public static TicketHoldingResponse from(TicketHolding holding) {
		return new TicketHoldingResponse(holding.getGrade(), holding.getExpiresAt(), holding.getCount());
	}
}
