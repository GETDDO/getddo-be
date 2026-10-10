package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 응모권 한 장의 처리 이력 한 건. 처리 직후의 상태와 만료 시각을 당시 값으로 남기며 수정·삭제하지 않는다.
 *
 * <p>같은 응모권의 이력은 {@code ticketVersion} 순서가 곧 처리 순서다. 이전 값은 직전 버전의 이력에서 읽는다.</p>
 */
@Getter
public final class TicketHistory {

	/** 저장 전에는 null. */
	private final UUID id;
	private final UUID ticketId;
	private final TicketOperationType operationType;
	/** 처리 후 응모권 버전. 지급은 1이다. */
	private final long ticketVersion;
	private final TicketStatus status;
	private final Instant expiresAt;
	/** 사용의 응모 ID. 다른 처리는 null. */
	private final UUID eventEntryId;
	/** 반환이 되돌리는 원본 사용 이력 ID. 반환이 아니면 null. */
	private final UUID originalUseHistoryId;
	/** 정정이 고치는 과거 이력 ID. 정정이 아니면 null. */
	private final UUID correctedHistoryId;
	private final String reason;
	private final Instant createdAt;

	public TicketHistory(UUID id, UUID ticketId, TicketOperationType operationType, long ticketVersion,
			TicketStatus status, Instant expiresAt, UUID eventEntryId, UUID originalUseHistoryId,
			UUID correctedHistoryId, String reason, Instant createdAt) {
		requireConsistent(operationType, ticketVersion, status, eventEntryId, originalUseHistoryId,
				correctedHistoryId);
		this.id = id;
		this.ticketId = ticketId;
		this.operationType = operationType;
		this.ticketVersion = ticketVersion;
		this.status = status;
		this.expiresAt = expiresAt;
		this.eventEntryId = eventEntryId;
		this.originalUseHistoryId = originalUseHistoryId;
		this.correctedHistoryId = correctedHistoryId;
		this.reason = reason;
		this.createdAt = createdAt;
	}

	/**
	 * 저장된 새 응모권의 지급 이력을 만든다. 처리 시각·상태·만료 시각은 응모권의 지급 당시 값과 같다.
	 *
	 * @param issued 저장되어 ID가 있는 응모권
	 * @param reason 이력에 표시할 사유
	 */
	public static TicketHistory grant(Ticket issued, String reason) {
		Objects.requireNonNull(issued.getId(), "issued.id");
		return new TicketHistory(null, issued.getId(), TicketOperationType.GRANT, issued.getVersion(),
				issued.getStatus(), issued.getExpiresAt(), null, null, null, reason, issued.getCreatedAt());
	}

	/**
	 * 응모 사용 이력을 만든다. 처리 시각·상태·만료 시각은 사용된 응모권의 현재 값과 같다.
	 *
	 * @param used 사용 처리된, 저장된 응모권
	 * @param eventEntryId 응모 ID
	 * @param reason 이력에 표시할 사유
	 */
	public static TicketHistory use(Ticket used, UUID eventEntryId, String reason) {
		Objects.requireNonNull(used.getId(), "used.id");
		return new TicketHistory(null, used.getId(), TicketOperationType.USE, used.getVersion(), used.getStatus(),
				used.getExpiresAt(), eventEntryId, null, null, reason, used.getUpdatedAt());
	}

	/**
	 * 응모 반환 이력을 만든다. 되돌리는 사용 이력에 연결하며, 같은 사용 이력은 한 번만 반환할 수 있다.
	 *
	 * @param refunded 반환 처리된, 저장된 응모권
	 * @param originalUseHistoryId 반환이 되돌리는 사용 이력 ID
	 * @param reason 이력에 표시할 사유
	 */
	public static TicketHistory refund(Ticket refunded, UUID originalUseHistoryId, String reason) {
		Objects.requireNonNull(refunded.getId(), "refunded.id");
		return new TicketHistory(null, refunded.getId(), TicketOperationType.REFUND, refunded.getVersion(),
				refunded.getStatus(), refunded.getExpiresAt(), null, originalUseHistoryId, null, reason,
				refunded.getUpdatedAt());
	}

	/**
	 * 만료 이력을 만든다.
	 *
	 * @param expired 만료 처리된, 저장된 응모권
	 * @param reason 이력에 표시할 사유
	 */
	public static TicketHistory expire(Ticket expired, String reason) {
		Objects.requireNonNull(expired.getId(), "expired.id");
		return new TicketHistory(null, expired.getId(), TicketOperationType.EXPIRE, expired.getVersion(),
				expired.getStatus(), expired.getExpiresAt(), null, null, null, reason, expired.getUpdatedAt());
	}

	/** DB의 이력 CHECK와 같은 조합만 허용해, 잘못된 이력이 저장 단계의 제약 위반이 아니라 여기서 먼저 드러나게 한다. */
	private static void requireConsistent(TicketOperationType operationType, long ticketVersion, TicketStatus status,
			UUID eventEntryId, UUID originalUseHistoryId, UUID correctedHistoryId) {
		Objects.requireNonNull(operationType, "operationType");
		Objects.requireNonNull(status, "status");
		boolean grant = operationType == TicketOperationType.GRANT;
		if (grant ? ticketVersion != 1 : ticketVersion < 2) {
			throw new IllegalArgumentException("지급 이력의 버전은 1이고 다른 이력의 버전은 2 이상이어야 한다.");
		}
		if ((operationType == TicketOperationType.USE) != (eventEntryId != null)) {
			throw new IllegalArgumentException("응모 ID는 사용 이력에만 있어야 한다.");
		}
		if ((operationType == TicketOperationType.REFUND) != (originalUseHistoryId != null)) {
			throw new IllegalArgumentException("원본 사용 이력 ID는 반환 이력에만 있어야 한다.");
		}
		if ((operationType == TicketOperationType.CORRECTION) != (correctedHistoryId != null)) {
			throw new IllegalArgumentException("정정 대상 이력 ID는 정정 이력에만 있어야 한다.");
		}
		TicketStatus expected = switch (operationType) {
			case GRANT -> TicketStatus.AVAILABLE;
			case USE -> TicketStatus.SPENT;
			case REFUND -> TicketStatus.RETURNED;
			case EXPIRE -> TicketStatus.EXPIRED;
			case CORRECTION -> status;
		};
		if (status != expected) {
			throw new IllegalArgumentException("처리 유형과 결과 상태가 맞지 않는다.");
		}
	}
}
