package com.getddo.core.audit.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.audit.domain.AuditLogCommand;
import com.getddo.core.audit.repository.AuditLogRepository;

/** 업무 변경과 같은 트랜잭션에서 감사 기록을 남기는 공용 진입점. */
@Service
@RequiredArgsConstructor
public class AuditLogService {

	private final AuditLogRepository auditLogRepository;

	/**
	 * 다른 Spring Bean의 업무 트랜잭션 안에서 호출한다. 저장 실패는 호출자에게 전파한다.
	 *
	 * <p>반환 ID는 영속화 시 발급되며 업무 트랜잭션의 commit을 보장하지 않는다.
	 * 동일 요청의 중복 기록 방지는 소비 기능이 담당한다.</p>
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public UUID record(AuditLogCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("Audit command is required");
		}
		return auditLogRepository.append(command);
	}
}
