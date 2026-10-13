package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 기존 실행 감사 이력의 불변 조회 모델. 원본 감사 행을 변경하지 않는다. */
@Getter
@EqualsAndHashCode
@Accessors(fluent = true)
public final class DrawExecutionAuditView {
	private final UUID id;
	private final UUID actorId;
	private final String action;
	private final String reason;
	private final String beforeData;
	private final String afterData;
	private final String requestId;
	private final Instant createdAt;

	/** 저장된 조회 필드를 보존하고 컬렉션은 외부 변경으로부터 보호한다. */
	public DrawExecutionAuditView(UUID id, UUID actorId, String action, String reason, String beforeData, String afterData, String requestId, Instant createdAt) {
		this.id = id;
		this.actorId = actorId;
		this.action = action;
		this.reason = reason;
		this.beforeData = beforeData;
		this.afterData = afterData;
		this.requestId = requestId;
		this.createdAt = createdAt;
	}
}
