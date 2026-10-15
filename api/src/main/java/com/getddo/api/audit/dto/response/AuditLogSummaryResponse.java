package com.getddo.api.audit.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.getddo.core.audit.domain.AuditLogEntry;

/** 목록에는 변경 전후 JSON을 포함하지 않고 기본 추적 정보만 공개한다. */
public record AuditLogSummaryResponse(UUID id, UUID actorId, String action, String targetType,
		UUID targetId, String reason, String requestId, Instant createdAt) {
	public static AuditLogSummaryResponse from(AuditLogEntry entry) {
		return new AuditLogSummaryResponse(entry.getId(), entry.getActorId(), entry.getAction(), entry.getTargetType(),
				entry.getTargetId(), entry.getReason(), entry.getRequestId(), entry.getCreatedAt());
	}
}
