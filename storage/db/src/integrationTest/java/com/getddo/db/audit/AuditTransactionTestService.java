package com.getddo.db.audit;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.audit.domain.AuditLogCommand;
import com.getddo.core.audit.service.AuditLogService;

/** 실제 업무 코드를 수정하지 않고 업무 변경과 감사 기록의 commit 경계를 재현한다. */
public class AuditTransactionTestService {

	private final JdbcTemplate jdbc;
	private final AuditLogService auditLogService;
	private final EntityManager entityManager;

	public AuditTransactionTestService(JdbcTemplate jdbc, AuditLogService auditLogService,
			EntityManager entityManager) {
		this.jdbc = jdbc;
		this.auditLogService = auditLogService;
		this.entityManager = entityManager;
	}

	@Transactional
	public UUID changeAndRecord(UUID userId, AuditLogCommand command, boolean failBusiness) {
		jdbc.update("update users set name = '변경 후' where id = unhex(replace(?, '-', ''))", userId.toString());
		UUID id = auditLogService.record(command);
		entityManager.flush();
		entityManager.clear();
		if (failBusiness) {
			throw new IllegalStateException("test business failure");
		}
		return id;
	}
}
