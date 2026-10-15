package com.getddo.api.audit.dto.request;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import org.springframework.format.annotation.DateTimeFormat;

import com.getddo.core.audit.domain.AuditLogQuery;

/** MVC가 UUID·UTC 시각을 해석하고 도메인 생성자가 조회 조건을 검증한다. */
@Getter
public final class AuditLogQueryRequest {
	private final UUID actorId;
	private final String action;
	private final String targetType;
	private final UUID targetId;
	private final Instant from;
	private final Instant to;

	public AuditLogQueryRequest(UUID actorId, String action, String targetType, UUID targetId,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
		this.actorId = actorId;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.from = from;
		this.to = to;
	}

	public AuditLogQuery toDomain(int page, int size) {
		return new AuditLogQuery(page, size, actorId, action, targetType, targetId, from, to);
	}
}
