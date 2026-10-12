package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** 저장된 결과의 식별자·선정 순서·생성 시각을 보존한다. */
@Getter
public final class DrawResult {
	private final UUID id;
	private final DrawSelection selection;
	private final Instant createdAt;

	/** DB에서 확정한 결과 ID·선정 내용·생성 시각을 복원하며 필수 값의 누락을 거절한다. */
	public DrawResult(UUID id, DrawSelection selection, Instant createdAt) {
		this.id = Objects.requireNonNull(id, "id");
		this.selection = Objects.requireNonNull(selection, "selection");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}
}
