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
	/** 이미 발급된 공개·실행·확인 식별자를 연결하며 필수값 누락은 저장 전에 거절한다. */
	public DrawPublicationExecutionEntity(UUID publicationId, UUID eventId, DrawPublicationExecution execution) {
		Objects.requireNonNull(execution, "execution");
		this.id = new DrawPublicationExecutionId(publicationId, execution.runId());
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.confirmationId = execution.confirmationId();
	}
	/** 할당된 복합키의 신규 연결도 persist로 저장하도록 신규 행 여부를 제공한다. */
	@Override public boolean isNew() { return newEntity; }
	/** 저장 또는 조회가 끝난 연결을 기존 영속 행으로 표시한다. */
	@PostLoad @PostPersist private void markPersisted() { newEntity = false; }
}
