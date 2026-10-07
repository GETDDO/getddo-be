package com.getddo.db.audit.repository;

import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.getddo.db.audit.entity.AuditLogEntity;

/** 저장 어댑터에서만 사용하는 JPA 추가 저장 인터페이스. */
public interface AuditLogJpaRepository extends Repository<AuditLogEntity, UUID> {

	AuditLogEntity save(AuditLogEntity entity);
}
