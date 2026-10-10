package com.getddo.db.entry.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

/**
 * 접수 완료된 응모 Entity. 추가만 하고 수정·삭제하지 않는다.
 *
 * <p>ID는 서버가 만들지 않고 클라이언트가 요청 전에 정한 UUID({@code Idempotency-Key})다. 직접 지정한 ID로 {@code save}를
 * 부르면 Spring Data가 새 행인지 알 수 없어 {@code merge}(조회 뒤 저장)로 가고, 같은 ID의 중복 응모가 조용히 통과할 수 있다.
 * {@link Persistable}로 항상 새 행으로 보아 {@code INSERT}를 하게 하고, 중복이면 PK 위반 예외가 나게 한다.</p>
 */
@Getter
@Entity
@Immutable
@Table(name = "event_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventEntryEntity implements Persistable<UUID> {

	@Id
	@Column(name = "id", nullable = false, updatable = false, length = 16)
	private UUID id;

	@Column(name = "participant_id", nullable = false, updatable = false, length = 16)
	private UUID participantId;

	@Column(name = "user_id", nullable = false, updatable = false, length = 16)
	private UUID userId;

	@Column(name = "requested_ticket_count", nullable = false, updatable = false)
	private int requestedTicketCount;

	@Column(name = "deducted_ticket_count", nullable = false, updatable = false)
	private int deductedTicketCount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Transient
	private boolean newEntity = true;

	public EventEntryEntity(UUID id, UUID participantId, UUID userId, int requestedTicketCount,
			int deductedTicketCount, Instant createdAt) {
		this.id = id;
		this.participantId = participantId;
		this.userId = userId;
		this.requestedTicketCount = requestedTicketCount;
		this.deductedTicketCount = deductedTicketCount;
		this.createdAt = createdAt;
	}

	@Override
	public boolean isNew() {
		return newEntity;
	}

	@PostLoad
	@PostPersist
	private void markPersisted() {
		newEntity = false;
	}
}
