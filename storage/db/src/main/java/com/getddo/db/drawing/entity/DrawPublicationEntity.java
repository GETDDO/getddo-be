package com.getddo.db.drawing.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import com.getddo.core.drawing.domain.DrawPublication;
import com.getddo.core.drawing.domain.DrawPublicationType;

/** 공개 당시의 불변 메타데이터. 업무 시각을 보존하므로 created_at을 상속하지 않는다. */
@Getter
@Entity
@Immutable
@Table(name = "draw_publications", uniqueConstraints = {
		@UniqueConstraint(name = "uq_draw_publications_event", columnNames = {"id", "event_id"}),
		@UniqueConstraint(name = "uq_draw_publications_1", columnNames = {"event_id", "revision"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrawPublicationEntity {
	@Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false, length = 16)
	private UUID id;
	@Column(name = "event_id", nullable = false, updatable = false, length = 16)
	private UUID eventId;
	@Column(name = "source_draw_id", nullable = false, updatable = false, length = 16)
	private UUID sourceDrawId;
	@Column(nullable = false, updatable = false)
	private int revision;
	@Enumerated(EnumType.STRING)
	@Column(name = "publication_type", nullable = false, updatable = false)
	private DrawPublicationType type;
	@Column(columnDefinition = "text", updatable = false)
	private String reason;
	@Column(name = "confirmed_by", updatable = false, length = 16)
	private UUID confirmedBy;
	@Column(name = "confirmed_at", updatable = false)
	private Instant confirmedAt;
	@Column(name = "published_at", nullable = false, updatable = false)
	private Instant publishedAt;

	/** 정상 최초 자동 공개에는 관리자 확인을 추가하지 않는다. */
	public DrawPublicationEntity(UUID eventId, UUID sourceDrawId, Instant publishedAt) {
		this.eventId = eventId;
		this.sourceDrawId = sourceDrawId;
		this.publishedAt = publishedAt;
		revision = 1;
		type = DrawPublicationType.INITIAL;
	}

	/** 검증된 공개 모델에서 새 버전을 구성한다. ID는 저장 시 발급한다. */
	public DrawPublicationEntity(DrawPublication publication) {
		eventId = publication.eventId();
		sourceDrawId = publication.sourceDrawId();
		revision = publication.revision();
		type = publication.type();
		reason = publication.reason();
		confirmedBy = publication.confirmedBy();
		confirmedAt = publication.confirmedAt();
		publishedAt = publication.publishedAt();
	}

	/** 실제 저장 시각과 실제 포함 결과를 복원한다. */
	public DrawPublication toDomain(List<DrawPublication.Entry> entries) {
		return new DrawPublication(id, eventId, sourceDrawId, revision, type, reason,
				confirmedBy, confirmedAt, publishedAt, entries);
	}
}
