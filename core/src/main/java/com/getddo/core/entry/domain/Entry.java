package com.getddo.core.entry.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

import com.getddo.core.common.exception.ErrorCode;
import com.getddo.core.ticket.domain.TicketGrade;

/**
 * 응모 요청 한 건의 결과. 접수 완료({@link EntryStatus#ACCEPTED})와 업무 규칙으로 거절된 이력({@link EntryStatus#REJECTED})을
 * 함께 담는다.
 *
 * <p>응모 ID는 클라이언트가 응모 요청 전에 정해 보내는 UUID({@code Idempotency-Key})다. 같은 ID로 다시 요청하면 새로
 * 처리하지 않고 이 결과를 돌려준다. 접수 건은 응모자와 차감이 있고, 거절 건은 응모자와 차감 없이 거절 코드·사유와 요청한
 * 등급별 장수만 남는다.</p>
 */
@Getter
public final class Entry {

	private final UUID id;
	/** 접수 완료된 응모의 응모자. 거절 건은 없다. */
	private final UUID participantId;
	private final UUID eventId;
	private final UUID userId;
	private final EntryStatus status;
	/** 사용자가 요청한 응모권 장수 합계. 접수된 응모는 요청한 만큼 모두 차감한다. */
	private final int requestedTicketCount;
	/** 등급별 요청 장수. 장수가 0인 등급도 키를 가진다. */
	private final Map<TicketGrade, Long> requestedTicketsByGrade;
	private final int deductedTicketCount;
	/** 거절 건의 오류 코드. 접수 건은 없다. */
	private final String rejectionCode;
	/** 거절 건의 사용자 안내 사유. 접수 건은 없다. */
	private final String rejectionReason;
	/** 결과를 기록한 시각. 접수 건은 접수 완료 시각이다. */
	private final Instant createdAt;

	public Entry(UUID id, UUID participantId, UUID eventId, UUID userId, EntryStatus status, int requestedTicketCount,
			Map<TicketGrade, Long> requestedTicketsByGrade, int deductedTicketCount, String rejectionCode,
			String rejectionReason, Instant createdAt) {
		this.id = Objects.requireNonNull(id, "id");
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.status = Objects.requireNonNull(status, "status");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		if (requestedTicketCount < 0 || deductedTicketCount < 0) {
			throw new IllegalArgumentException("응모권 수량은 0 이상이어야 한다.");
		}
		if (deductedTicketCount > requestedTicketCount) {
			throw new IllegalArgumentException("차감 수량은 요청 수량을 넘을 수 없다.");
		}
		if (status == EntryStatus.ACCEPTED) {
			Objects.requireNonNull(participantId, "participantId");
			if (rejectionCode != null || rejectionReason != null) {
				throw new IllegalArgumentException("접수된 응모에는 거절 코드와 사유가 없어야 한다.");
			}
		} else {
			if (participantId != null || deductedTicketCount != 0) {
				throw new IllegalArgumentException("거절된 응모에는 응모자와 차감이 없어야 한다.");
			}
			if (rejectionCode == null || rejectionCode.isBlank() || rejectionReason == null || rejectionReason.isBlank()) {
				throw new IllegalArgumentException("거절된 응모에는 거절 코드와 사유가 있어야 한다.");
			}
		}
		Map<TicketGrade, Long> byGrade = complete(Objects.requireNonNull(requestedTicketsByGrade, "requestedTicketsByGrade"));
		long gradeSum = 0;
		for (Long count : byGrade.values()) {
			if (count == null || count < 0) {
				throw new IllegalArgumentException("등급별 요청 장수는 0 이상이어야 한다.");
			}
			gradeSum += count;
		}
		if (gradeSum != requestedTicketCount) {
			throw new IllegalArgumentException("등급별 요청 장수의 합이 요청 수량과 같아야 한다.");
		}
		this.participantId = participantId;
		this.requestedTicketCount = requestedTicketCount;
		this.requestedTicketsByGrade = byGrade;
		this.deductedTicketCount = deductedTicketCount;
		this.rejectionCode = rejectionCode;
		this.rejectionReason = rejectionReason;
	}

	/** 요청한 만큼 모두 차감한 접수 완료 응모를 만든다. */
	public static Entry accepted(UUID id, EntryParticipant participant, Map<TicketGrade, Long> ticketsByGrade,
			Instant acceptedAt) {
		int total = Math.toIntExact(sum(ticketsByGrade));
		return new Entry(id, participant.getId(), participant.getEventId(), participant.getUserId(),
				EntryStatus.ACCEPTED, total, ticketsByGrade, total, null, null, acceptedAt);
	}

	/** 업무 규칙으로 거절된 시도의 이력을 만든다. 응모자와 차감 없이 요청 장수와 거절 사유만 남긴다. */
	public static Entry rejected(UUID id, UUID eventId, UUID userId, Map<TicketGrade, Long> ticketsByGrade,
			ErrorCode reason, Instant rejectedAt) {
		Objects.requireNonNull(reason, "reason");
		return new Entry(id, null, eventId, userId, EntryStatus.REJECTED, Math.toIntExact(sum(ticketsByGrade)),
				ticketsByGrade, 0, reason.getCode(), reason.getMessage(), rejectedAt);
	}

	public boolean isRejected() {
		return status == EntryStatus.REJECTED;
	}

	/** 등급별 차감 장수. 접수된 응모는 요청한 만큼 모두 차감하고, 거절된 응모는 차감이 없다. */
	public Map<TicketGrade, Long> getDeductedTicketsByGrade() {
		return isRejected() ? complete(Map.of()) : requestedTicketsByGrade;
	}

	private static long sum(Map<TicketGrade, Long> ticketsByGrade) {
		return ticketsByGrade.values().stream().mapToLong(Long::longValue).sum();
	}

	private static Map<TicketGrade, Long> complete(Map<TicketGrade, Long> ticketsByGrade) {
		Map<TicketGrade, Long> complete = new EnumMap<>(TicketGrade.class);
		for (TicketGrade grade : TicketGrade.values()) {
			complete.put(grade, ticketsByGrade.getOrDefault(grade, 0L));
		}
		return Collections.unmodifiableMap(complete);
	}
}
