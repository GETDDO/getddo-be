package com.getddo.core.audit.repository;

import java.util.UUID;

import com.getddo.core.audit.domain.AuditLogCommand;

/** 감사 기록의 추가 저장 계약. 조회·수정·삭제는 제공하지 않는다. */
public interface AuditLogRepository {

	UUID append(AuditLogCommand command);
}
