package com.getddo.db.drawing.entity;

import java.util.UUID;
import java.util.Objects;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;
import com.getddo.core.drawing.domain.DrawPublicationExecution;

/** 미충원을 포함한 공개 버전의 실행 확인 연결. 공개·확인 시각은 원본 불변 행이 보존한다. */
@Getter
@Entity
@Immutable
@Table(name = "draw_publication_executions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrawPublicationExecutionEntity implements Persistable<DrawPublicationExecutionId> {
	@EmbeddedId private DrawPublicationExecutionId id;
	@Column(name = "event_id", nullable = false, updatable = false, length = 16) private UUID eventId;
	@Column(name = "confirmation_id", nullable = false, updatable = false, length = 16) private UUID confirmationId;
	@Transient private boolean newEntity = true;
	public DrawPublicationExecutionEntity(UUID publicationId, UUID eventId, DrawPublicationExecution execution) {
		Objects.requireNonNull(execution, "execution");
		this.id = new DrawPublicationExecutionId(publicationId, execution.runId());
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.confirmationId = execution.confirmationId();
	}
	@Override public boolean isNew() { return newEntity; }
	@PostLoad @PostPersist private void markPersisted() { newEntity = false; }
}
