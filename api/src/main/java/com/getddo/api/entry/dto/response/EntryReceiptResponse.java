package com.getddo.api.entry.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.getddo.core.entry.domain.EntryReceipt;
import com.getddo.core.ticket.domain.TicketGrade;

/**
 * 접수 완료된 응모 영수증의 공개 필드다.
 *
 * <p>접수된 응모는 요청한 응모권을 모두 차감하므로 요청 장수와 차감 장수가 같다. 거절된 시도는 저장하지 않아 거절 필드는
 * 항상 {@code null}이고, 요청 시각은 접수 시각과 같다.</p>
 *
 * @param id 응모 ID. 요청의 {@code Idempotency-Key}와 같다
 * @param requestedTicketCount 요청한 응모권 합계
 * @param requestedTicketsByGrade 등급별 요청 장수. 장수가 0인 등급도 키를 가진다
 * @param deductedTicketCount 차감한 응모권 합계
 * @param deductedTicketsByGrade 등급별 차감 장수. 장수가 0인 등급도 키를 가진다
 */
public record EntryReceiptResponse(
		UUID id,
		UUID eventId,
		String eventTitle,
		long requestedTicketCount,
		Map<TicketGrade, Long> requestedTicketsByGrade,
		long deductedTicketCount,
		Map<TicketGrade, Long> deductedTicketsByGrade,
		EntryStatus status,
		Instant requestedAt,
		Instant acceptedAt,
		String rejectionCode,
		String rejectionReason) {

	/** 서비스가 돌려준 영수증을 응답으로 옮긴다. */
	public static EntryReceiptResponse from(EntryReceipt receipt) {
		return new EntryReceiptResponse(receipt.getEntryId(), receipt.getEventId(), receipt.getEventTitle(),
				receipt.getTicketCount(), receipt.getTicketsByGrade(), receipt.getTicketCount(),
				receipt.getTicketsByGrade(), EntryStatus.ACCEPTED, receipt.getAcceptedAt(), receipt.getAcceptedAt(),
				null, null);
	}
}
