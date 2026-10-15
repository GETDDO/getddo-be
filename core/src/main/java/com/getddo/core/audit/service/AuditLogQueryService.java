package com.getddo.core.audit.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.audit.domain.AuditLogEntry;
import com.getddo.core.audit.domain.AuditLogQuery;
import com.getddo.core.audit.exception.AuditQueryErrorCode;
import com.getddo.core.audit.repository.AuditLogQueryRepository;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;

/** 활성 관리자 권한을 확인하고 저장 원본에서 공개 가능한 감사 정보만 반환한다. */
@Service
@RequiredArgsConstructor
public class AuditLogQueryService {
	private final AuditLogQueryRepository repository;

	@Transactional(readOnly = true)
	public PageResult<AuditLogEntry> findAll(User actor, AuditLogQuery query) {
		requireAdmin(actor);
		return repository.findAll(query).map(AuditLogEntry::publicView);
	}

	@Transactional(readOnly = true)
	public AuditLogEntry findById(User actor, UUID id) {
		requireAdmin(actor);
		return repository.findById(id).orElseThrow(() -> new BusinessException(AuditQueryErrorCode.NOT_FOUND)).publicView();
	}

	private static void requireAdmin(User actor) {
		if (actor == null || actor.getRole() != UserRole.ADMIN || actor.getStatus() != UserStatus.ACTIVE) {
			throw new BusinessException(AuditQueryErrorCode.ADMIN_REQUIRED);
		}
	}
}
