package com.getddo.db.audit.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.getddo.core.audit.domain.AuditLogCommand;
import com.getddo.db.audit.entity.AuditLogEntity;

/** 입력 스냅샷을 저장 Entity로 옮긴다. ID와 시각은 공통 JPA 기반이 생성한다. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AuditLogMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	AuditLogEntity toEntity(AuditLogCommand command);
}
