package com.getddo.api.ticket.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.getddo.core.ticket.domain.TicketTransactionType;
import com.getddo.core.ticket.domain.TicketTransactionView;

/**
 * T02 응모권 이력 한 건의 공개 필드다.
 *
 * @param id 원장 ID
 * @param walletId 지갑 ID
 * @param transactionType 거래 유형
 * @param quantity 수량. 지급·반환은 양수, 차감·만료·회수는 음수
 * @param balanceAfter 해당 지갑의 처리 직후 잔액. 전체 지갑 합계가 아니다
 * @param reason 거래 사유
 * @param createdAt 거래 시각
 * @param expiresAt 입금분의 만료 시각. 입금이 아니면 null
 * @param eventId 관련 이벤트 ID. 없으면 null
 * @param eventEntryId 관련 응모 ID. 없으면 null
 * @param missionId 관련 미션 ID. 없으면 null
 * @param gameId 관련 게임 ID. 없으면 null
 * @param attendanceDate 출석 보상이면 출석한 KST 날짜. 아니면 null
 * @param relatedLedgerId 연결된 원장 ID. 없으면 null
 * @param refundOfId 반환 원본 원장 ID. 반환이 아니면 null
 */
public record TicketTransactionResponse(UUID id, UUID walletId, TicketTransactionType transactionType,
		long quantity, long balanceAfter, String reason, Instant createdAt, Instant expiresAt, UUID eventId,
		UUID eventEntryId, UUID missionId, UUID gameId, LocalDate attendanceDate, UUID relatedLedgerId,
		UUID refundOfId) {

	/**
	 * 이력 한 건을 응답으로 옮긴다.
	 *
	 * @param view 서비스가 반환한 이력
	 * @return 공개 필드만 담은 응답 DTO
	 */
	public static TicketTransactionResponse from(TicketTransactionView view) {
		return new TicketTransactionResponse(view.getId(), view.getWalletId(), view.getTransactionType(),
				view.getQuantity(), view.getBalanceAfter(), view.getReason(), view.getCreatedAt(),
				view.getExpiresAt(), view.getEventId(), view.getEventEntryId(), view.getMissionId(),
				view.getGameId(), view.getAttendanceDate(), view.getRelatedLedgerId(), view.getRefundOfId());
	}
}
