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
}
