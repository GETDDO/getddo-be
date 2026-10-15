package com.getddo.db.audit.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.getddo.core.audit.domain.AuditLogEntry;
import com.getddo.db.audit.entity.AuditLogEntity;

/** 기존 감사 Entity를 읽기 모델로 변환하며 공개 필드 선택은 core에 맡긴다. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AuditLogQueryMapper {
	AuditLogEntry toDomain(AuditLogEntity entity);
}
