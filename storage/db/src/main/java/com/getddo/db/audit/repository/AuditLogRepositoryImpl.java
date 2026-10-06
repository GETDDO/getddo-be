package com.getddo.db.audit.repository;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.audit.domain.AuditLogCommand;
import com.getddo.core.audit.repository.AuditLogRepository;
import com.getddo.db.audit.mapper.AuditLogMapper;

/** 호출자의 트랜잭션에 감사 Entity를 추가한다. */
@Repository
@RequiredArgsConstructor
public class AuditLogRepositoryImpl implements AuditLogRepository {

	private final AuditLogJpaRepository auditLogJpaRepository;
	private final AuditLogMapper auditLogMapper;

	@Override
	public UUID append(AuditLogCommand command) {
		return auditLogJpaRepository.save(auditLogMapper.toEntity(command)).getId();
	}
}
