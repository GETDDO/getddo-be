package com.getddo.api.ticket.dto.response;

import java.time.Instant;
import java.util.List;

import com.getddo.core.ticket.domain.MyTicketWallets;

/**
 * T01 내 응모권 지갑 조회 응답이다. 사용 가능 잔액과 지갑 상태는 모두 같은 {@code serverTime} 기준이다.
 *
 * @param availableBalance 조회 시각에 사용할 수 있는 잔액 합계
 * @param wallets 만료된 지갑을 포함한 전체 지갑. 만료월 최신순
 * @param serverTime 판정 기준 서버 시각
 */
public record MyWalletsResponse(long availableBalance, List<TicketWalletResponse> wallets, Instant serverTime) {

	/**
	 * 지갑 조회 결과를 응답으로 옮긴다.
	 *
	 * @param result 서비스가 반환한 지갑 조회 결과
	 * @return 공개 필드만 담은 응답 DTO
	 */
	public static MyWalletsResponse from(MyTicketWallets result) {
		return new MyWalletsResponse(result.getAvailableBalance(),
				result.getWallets().stream().map(TicketWalletResponse::from).toList(), result.getServerTime());
	}
}
