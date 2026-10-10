package com.getddo.db.entry.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 이벤트 응모자 Entity. 사용자는 이벤트마다 한 행이고, 추가 응모는 같은 행의 누적 차감 수량에 쌓인다.
 *
 * <p>행 생성 시각은 응모 접수 시각과 같아야 하므로 공통 Entity의 {@code @CreatedDate}에 맡기지 않고 직접 채운다(응모권
 * Entity와 같은 이유). 이벤트·사용자 참조는 연관관계 없이 UUID 값으로만 둔다.</p>
 */
@Getter
@Entity
@Table(name = "event_participants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventParticipantEntity {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false, length = 16)
	private UUID id;

	@Column(name = "event_id", nullable = false, updatable = false, length = 16)
	private UUID eventId;

	@Column(name = "user_id", nullable = false, updatable = false, length = 16)
	private UUID userId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "used_ticket_count", nullable = false)
	private long usedTicketCount;

	public EventParticipantEntity(UUID eventId, UUID userId, Instant createdAt, long usedTicketCount) {
		this.eventId = eventId;
		this.userId = userId;
		this.createdAt = createdAt;
		this.usedTicketCount = usedTicketCount;
	}

	/** 누적 차감 수량을 늘린다. */
	public void addUsedTicketCount(long amount) {
		this.usedTicketCount = Math.addExact(this.usedTicketCount, amount);
	}
}
