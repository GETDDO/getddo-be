package com.getddo.api.entry.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import lombok.Builder;

import com.getddo.core.entry.domain.Entry;
import com.getddo.core.entry.domain.EntryReceipt;
import com.getddo.core.entry.domain.EntryStatus;
import com.getddo.core.entry.domain.EntrySummary;
import com.getddo.core.ticket.domain.TicketGrade;

/**
 * 응모 요청 결과의 공개 필드다. 접수 완료 영수증(E04)과 접수·거절 이력(E05·E06)에 함께 쓴다.
 *
 * <p>접수된 응모는 요청한 응모권을 모두 차감하므로 요청 장수와 차감 장수가 같고, 접수 시각이 있으며 거절 필드는 {@code null}이다.
 * 거절된 요청은 차감 장수가 0이고 접수 시각이 {@code null}이며 거절 코드와 사유가 있다. 요청 시각은 결과를 기록한 시각이다.</p>
 *
 * @param id 응모 ID. 요청의 {@code Idempotency-Key}와 같다
 * @param requestedTicketCount 요청한 응모권 합계
 * @param requestedTicketsByGrade 등급별 요청 장수. 장수가 0인 등급도 키를 가진다
 * @param deductedTicketCount 차감한 응모권 합계
 * @param deductedTicketsByGrade 등급별 차감 장수. 장수가 0인 등급도 키를 가진다
 * @param acceptedAt 접수 시각. 거절된 요청은 {@code null}
 */
@Builder
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

	/** 서비스가 돌려준 접수 완료 영수증을 응답으로 옮긴다. */
	public static EntryReceiptResponse from(EntryReceipt receipt) {
		return EntryReceiptResponse.builder()
				.id(receipt.getEntryId())
				.eventId(receipt.getEventId())
				.eventTitle(receipt.getEventTitle())
				.requestedTicketCount(receipt.getTicketCount())
				.requestedTicketsByGrade(receipt.getTicketsByGrade())
				.deductedTicketCount(receipt.getTicketCount())
				.deductedTicketsByGrade(receipt.getTicketsByGrade())
				.status(EntryStatus.ACCEPTED)
				.requestedAt(receipt.getAcceptedAt())
				.acceptedAt(receipt.getAcceptedAt())
				.build();
	}

	/** 응모 이력 한 건(접수 또는 거절)을 응모 결과 응답으로 옮긴다. */
	public static EntryReceiptResponse from(EntrySummary summary) {
		Entry entry = summary.getEntry();
		return EntryReceiptResponse.builder()
				.id(entry.getId())
				.eventId(entry.getEventId())
				.eventTitle(summary.getEventTitle())
				.requestedTicketCount(entry.getRequestedTicketCount())
				.requestedTicketsByGrade(entry.getRequestedTicketsByGrade())
				.deductedTicketCount(entry.getDeductedTicketCount())
				.deductedTicketsByGrade(entry.getDeductedTicketsByGrade())
				.status(entry.getStatus())
				.requestedAt(entry.getCreatedAt())
				.acceptedAt(entry.isRejected() ? null : entry.getCreatedAt())
				.rejectionCode(entry.getRejectionCode())
				.rejectionReason(entry.getRejectionReason())
				.build();
	}
}
