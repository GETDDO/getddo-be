package com.getddo.db.ticket.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.core.ticket.domain.TicketStatus;

/**
 * 응모권 처리 이력 Entity. 수정·삭제 없이 추가만 하므로 모든 컬럼을 갱신 불가로 매핑한다.
 *
 * <p><b>{@code BaseEntity}를 상속하지 않는 이유.</b> 지급 이력의 {@code created_at}은 응모권의 생성 시각, 지급 시각과
 * 같아야 하므로 공통 Entity의 {@code @CreatedDate}에 맡기지 않고 직접 채운다(ADR-0001의 예외, 응모권 Entity에 한정).
 * ID는 공통 Entity와 같은 UUID v7이다. 응모권·응모·다른 이력 참조는 연관관계 없이 UUID 값으로만 둔다.</p>
 */
@Getter
@Entity
@Table(name = "ticket_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketHistoryEntity {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false, length = 16)
	private UUID id;

	@Column(name = "ticket_id", nullable = false, updatable = false, length = 16)
	private UUID ticketId;

	@Column(name = "original_use_history_id", updatable = false, length = 16)
	private UUID originalUseHistoryId;

	@Column(name = "corrected_history_id", updatable = false, length = 16)
	private UUID correctedHistoryId;

	@Column(name = "event_entry_id", updatable = false, length = 16)
	private UUID eventEntryId;

	@Enumerated(EnumType.STRING)
	@Column(name = "operation_type", nullable = false, updatable = false)
	private TicketOperationType operationType;

	@Column(name = "ticket_version", nullable = false, updatable = false)
	private long ticketVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, updatable = false)
	private TicketStatus status;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "reason", nullable = false, updatable = false, columnDefinition = "text")
	private String reason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Builder
	private TicketHistoryEntity(UUID ticketId, UUID originalUseHistoryId, UUID correctedHistoryId,
			UUID eventEntryId, TicketOperationType operationType, long ticketVersion, TicketStatus status,
			Instant expiresAt, String reason, Instant createdAt) {
		this.ticketId = ticketId;
		this.originalUseHistoryId = originalUseHistoryId;
		this.correctedHistoryId = correctedHistoryId;
		this.eventEntryId = eventEntryId;
		this.operationType = operationType;
		this.ticketVersion = ticketVersion;
		this.status = status;
		this.expiresAt = expiresAt;
		this.reason = reason;
		this.createdAt = createdAt;
	}
}
