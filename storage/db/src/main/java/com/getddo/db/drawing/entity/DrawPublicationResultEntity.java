package com.getddo.db.drawing.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

import com.getddo.core.drawing.domain.DrawPublication;

/** 실제 공개 기록. 취소된 결과도 이 기록에서 제거하지 않는다. */
@Getter
@Entity
@Immutable
@Table(name = "draw_publication_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrawPublicationResultEntity implements Persistable<DrawPublicationResultId> {
	@EmbeddedId
	private DrawPublicationResultId id;
	@Column(name = "event_id", nullable = false, updatable = false, length = 16)
	private UUID eventId;
	@Column(name = "draw_run_id", nullable = false, updatable = false, length = 16)
	private UUID runId;
	@Column(name = "confirmation_id", updatable = false, length = 16)
	private UUID confirmationId;
	@Transient
	private boolean newEntity = true;

	/** 공개 버전·결과 복합키와 원본 실행·관리자 확인 참조를 저장 값으로 구성한다. */
	public DrawPublicationResultEntity(UUID publicationId, UUID eventId, DrawPublication.Entry entry) {
		id = new DrawPublicationResultId(publicationId, entry.getResultId());
		this.eventId = eventId;
		runId = entry.getRunId();
		confirmationId = entry.getConfirmationId();
	}

	/** 저장된 결과 ID·원본 실행 ID·선택적인 확인 ID를 공개 포함 결과로 복원한다. */
	public DrawPublication.Entry toDomain() {
		return new DrawPublication.Entry(id.getResultId(), runId, confirmationId);
	}

	/** 할당된 복합키의 신규 행도 merge가 아닌 persist로 저장할 수 있도록 신규 상태를 반환한다. */
	@Override
	public boolean isNew() {
		return newEntity;
	}

	/** 저장 또는 로드가 완료된 공개 포함 행을 기존 영속 행으로 표시한다. */
	@PostLoad
	@PostPersist
	private void markPersisted() {
		newEntity = false;
	}
}
