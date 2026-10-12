package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 공개 버전과 실제 포함 결과를 보존한다. 현재 당첨 취소 여부로 과거 목록을 바꾸지 않는다. */
@Getter
@EqualsAndHashCode
@Accessors(fluent = true)
public final class DrawPublication {
	private final UUID id;
	private final UUID eventId;
	private final UUID sourceDrawId;
	private final int revision;
	private final DrawPublicationType type;
	private final String reason;
	private final UUID confirmedBy;
	private final Instant confirmedAt;
	private final Instant publishedAt;
	private final List<Entry> entries;

	public DrawPublication(UUID id, UUID eventId, UUID sourceDrawId, int revision,
		DrawPublicationType type, String reason, UUID confirmedBy, Instant confirmedAt,
		Instant publishedAt, List<Entry> entries) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(eventId, "eventId");
		Objects.requireNonNull(sourceDrawId, "sourceDrawId");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(publishedAt, "publishedAt");
		entries = List.copyOf(entries);
		if (revision < 1 || (type == DrawPublicationType.INITIAL && revision != 1)
			|| (type == DrawPublicationType.UPDATE && revision < 2)) {
			throw new IllegalArgumentException("invalid publication revision");
		}
		if ((confirmedBy == null) != (confirmedAt == null)
			|| (confirmedAt != null && confirmedAt.isAfter(publishedAt))) {
			throw new IllegalArgumentException("invalid confirmation time");
		}
		if (type == DrawPublicationType.UPDATE && (confirmedBy == null || reason == null || reason.isBlank())) {
			throw new IllegalArgumentException("update requires confirmation and reason");
		}
		if (entries.stream().map(Entry::resultId).distinct().count() != entries.size()) {
			throw new IllegalArgumentException("duplicate publication result");
		}

		this.id = id;
		this.eventId = eventId;
		this.sourceDrawId = sourceDrawId;
		this.revision = revision;
		this.type = type;
		this.reason = reason;
		this.confirmedBy = confirmedBy;
		this.confirmedAt = confirmedAt;
		this.publishedAt = publishedAt;
		this.entries = entries;
	}

	/** 유지 결과도 원본 실행과 원래 관리자 확인 식별자를 그대로 참조한다. */
	@Getter
	@EqualsAndHashCode
	@Accessors(fluent = true)
	public static final class Entry {
		private final UUID resultId;
		private final UUID runId;
		private final UUID confirmationId;

		public Entry(UUID resultId, UUID runId, UUID confirmationId) {
			Objects.requireNonNull(resultId, "resultId");
			Objects.requireNonNull(runId, "runId");

			this.resultId = resultId;
			this.runId = runId;
			this.confirmationId = confirmationId;
		}
	}
}
