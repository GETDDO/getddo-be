package com.getddo.api.ticket.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.getddo.core.ticket.domain.MyTickets;
import com.getddo.core.ticket.domain.TicketGrade;

/**
 * T01 내 응모권 보유 조회 응답이다. 모든 값은 같은 {@code serverTime} 기준으로 사용 가능한 응모권만 센다.
 *
 * @param availableCount 조회 시각에 사용할 수 있는 실제 장수 합계. 등급과 관계없이 1장은 1이다
 * @param countByGrade 등급별 사용 가능한 장수. 장수가 0인 등급도 포함한다
 * @param holdings 등급·만료 시각별 묶음. 만료가 임박한 순서
 * @param serverTime 판정 기준 서버 시각
 */
public record MyTicketsResponse(long availableCount, Map<TicketGrade, Long> countByGrade,
		List<TicketHoldingResponse> holdings, Instant serverTime) {

	/**
	 * 보유 조회 결과를 응답으로 옮긴다.
	 *
	 * @param result 서비스가 반환한 보유 조회 결과
	 * @return 공개 필드만 담은 응답 DTO
	 */
	public static MyTicketsResponse from(MyTickets result) {
		return new MyTicketsResponse(result.getAvailableCount(), result.getCountByGrade(),
				result.getHoldings().stream().map(TicketHoldingResponse::from).toList(), result.getServerTime());
	}
}
