package com.getddo.core.audit.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;

import com.getddo.core.audit.exception.AuditQueryErrorCode;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.PageQuery;

/** 감사 목록의 페이지·문자열·UTC [from,to) 범위를 생성 시 검증한다. */
@Getter
public final class AuditLogQuery {
	private static final Instant MIN_TIME = Instant.parse("1000-01-01T00:00:00Z");
	private static final Instant MAX_TIME = Instant.parse("9999-12-31T23:59:59.999999Z");
	private final PageQuery page;
	private final UUID actorId;
	private final String action;
	private final String targetType;
	private final UUID targetId;
	private final Instant from;
	private final Instant to;

	public AuditLogQuery(int page, int size, UUID actorId, String action, String targetType,
			UUID targetId, Instant from, Instant to) {
		if (page < 1 || size < 1 || size > 100 || outsideDatabaseRange(from) || outsideDatabaseRange(to)
				|| (from != null && to != null && !from.isBefore(to))) {
			throw new BusinessException(AuditQueryErrorCode.INVALID_QUERY);
		}
		this.page = new PageQuery(page, size);
		this.actorId = actorId;
		this.action = normalize(action);
		this.targetType = normalize(targetType);
		this.targetId = targetId;
		this.from = from;
		this.to = to;
	}

	private static String normalize(String value) {
		if (value == null) return null;
		String normalized = value.strip();
		if (normalized.codePointCount(0, normalized.length()) > 60) {
			throw new BusinessException(AuditQueryErrorCode.INVALID_QUERY);
		}
		return normalized.isEmpty() ? null : normalized;
	}

	private static boolean outsideDatabaseRange(Instant value) {
		return value != null && (value.isBefore(MIN_TIME) || value.isAfter(MAX_TIME));
	}
}
