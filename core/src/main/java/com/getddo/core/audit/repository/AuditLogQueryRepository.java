package com.getddo.core.audit.repository;

import java.util.Optional;
import java.util.UUID;

import com.getddo.core.audit.domain.AuditLogEntry;
import com.getddo.core.audit.domain.AuditLogQuery;
import com.getddo.core.common.pagination.PageResult;

/** 기존 추가 저장 계약과 분리한 감사 읽기 계약이다. */
public interface AuditLogQueryRepository {
	PageResult<AuditLogEntry> findAll(AuditLogQuery query);
	Optional<AuditLogEntry> findById(UUID id);
}
