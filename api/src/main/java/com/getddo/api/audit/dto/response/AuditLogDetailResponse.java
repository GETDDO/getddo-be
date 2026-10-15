package com.getddo.api.audit.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.getddo.core.audit.domain.AuditLogEntry;

/** 서비스에서 공개 범위를 선택한 변경 근거만 상세에 포함한다. */
public record AuditLogDetailResponse(UUID id, UUID actorId, String action, String targetType,
		UUID targetId, String reason, String requestId, Instant createdAt,
		Map<String, Object> beforeData, Map<String, Object> afterData) {
	public static AuditLogDetailResponse from(AuditLogEntry entry) {
		return new AuditLogDetailResponse(entry.getId(), entry.getActorId(), entry.getAction(), entry.getTargetType(),
				entry.getTargetId(), entry.getReason(), entry.getRequestId(), entry.getCreatedAt(),
				entry.getBeforeData(), entry.getAfterData());
	}
}
