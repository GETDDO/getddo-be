package com.getddo.api.ticket.dto.response;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import com.getddo.core.ticket.domain.TicketWalletStatus;
import com.getddo.core.ticket.domain.TicketWalletView;

/**
 * 만료 묶음별 응모권 지갑 한 개의 공개 필드다.
 *
 * @param id 지갑 ID
 * @param expiryMonth 사용 가능한 마지막 KST 월. {@code YYYY-MM}으로 직렬화한다
 * @param validFrom 첫 입금 시각
 * @param expiresAt 만료 시각
 * @param balance 저장된 잔액. 만료된 지갑이면 사용할 수 없는 수량이다
 * @param status 조회 시각 기준 상태
 */
public record TicketWalletResponse(UUID id, YearMonth expiryMonth, Instant validFrom, Instant expiresAt,
		long balance, TicketWalletStatus status) {

	/**
	 * 조회 시각 기준으로 판정한 지갑을 응답으로 옮긴다. 도메인의 만료월(그 달 1일)을 업무월로 바꾼다.
	 *
	 * @param wallet 서비스가 반환한 지갑
	 * @return 공개 필드만 담은 응답 DTO
	 */
	public static TicketWalletResponse from(TicketWalletView wallet) {
		return new TicketWalletResponse(wallet.getId(), YearMonth.from(wallet.getExpiryMonth()),
				wallet.getValidFrom(), wallet.getExpiresAt(), wallet.getBalance(), wallet.getStatus());
	}
}
