package com.getddo.core.audit.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import lombok.Getter;

/** 저장된 감사 행을 읽으며 공개 시에는 원본을 바꾸지 않는 별도 투영을 만든다. */
@Getter
public final class AuditLogEntry {
	private final UUID id;
	private final UUID actorId;
	private final String action;
	private final String targetType;
	private final UUID targetId;
	private final String reason;
	private final String requestId;
	private final Instant createdAt;
	private final Map<String, Object> beforeData;
	private final Map<String, Object> afterData;

	public AuditLogEntry(UUID id, UUID actorId, String action, String targetType, UUID targetId,
			String reason, String requestId, Instant createdAt, Map<String, Object> beforeData, Map<String, Object> afterData) {
		this.id = id;
		this.actorId = actorId;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.reason = reason;
		this.requestId = requestId;
		this.createdAt = createdAt;
		this.beforeData = copy(beforeData);
		this.afterData = copy(afterData);
	}

	/** 공개한 작업·대상·필드·자료형만 남겨 원본과 분리한 조회 결과를 만든다. */
	public AuditLogEntry publicView() {
		return new AuditLogEntry(id, actorId, action, targetType, targetId, reason, requestId, createdAt,
				AuditLogDataProjection.project(action, targetType, beforeData),
				AuditLogDataProjection.project(action, targetType, afterData));
	}

	private static Map<String, Object> copy(Map<String, Object> source) {
		return source == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(source));
	}
}
