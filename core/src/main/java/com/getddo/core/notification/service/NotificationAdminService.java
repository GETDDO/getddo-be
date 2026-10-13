package com.getddo.core.notification.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import com.getddo.core.audit.domain.AuditLogCommand;
import com.getddo.core.audit.service.AuditLogService;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.notification.domain.AdminNotification;
import com.getddo.core.notification.domain.AdminNotificationJob;
import com.getddo.core.notification.domain.AdminNotificationJobResult;
import com.getddo.core.notification.domain.NotificationDeliveryQuery;
import com.getddo.core.notification.domain.NotificationJobQuery;
import com.getddo.core.notification.exception.NotificationAdminErrorCode;
import com.getddo.core.notification.repository.NotificationAdminRepository;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;

/** 활성 관리자에게 알림 조회와 최종 실패 복구 접수를 제공한다. */
@Service
@RequiredArgsConstructor
public class NotificationAdminService {
	private final NotificationAdminRepository repository;
	private final AuditLogService audit;
	private final TimeProvider time;

	/** 활성 관리자 권한을 확인하고 작업 목록을 조회한다. */
	@Transactional(readOnly = true)
	public PageResult<AdminNotificationJobResult> findJobs(User actor, NotificationJobQuery query) {
		requireAdmin(actor);
		PageResult<AdminNotificationJob> jobs = repository.findJobs(query);
		Instant now = time.now();
		return jobs.map(job -> new AdminNotificationJobResult(job, job.isRetryable(now)));
	}

	/** 활성 관리자 권한을 확인하고 작업 상세를 조회한다. 대상이 없으면 업무 예외를 던진다. */
	@Transactional(readOnly = true)
	public AdminNotificationJobResult findJob(User actor, UUID id) {
		requireAdmin(actor);
		AdminNotificationJob job = repository.findJob(id).orElseThrow(NotificationAdminService::notFound);
		return new AdminNotificationJobResult(job, job.isRetryable(time.now()));
	}

	/** 활성 관리자에게 읽음 상태를 변경하지 않는 발송 이력 조회를 제공한다. */
	@Transactional(readOnly = true)
	public PageResult<AdminNotification> findDeliveries(User actor, NotificationDeliveryQuery query) {
		requireAdmin(actor);
		return repository.findDeliveries(query);
	}

	/** 복구 접수와 감사 기록을 함께 커밋한다. 잠금 대기 후 현재 시각으로 시작 안내 자격을 검사한다. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public AdminNotificationJobResult retryJob(User actor, UUID id) {
		requireAdmin(actor);
		AdminNotificationJob before = repository.lockJob(id).orElseThrow(NotificationAdminService::notFound);
		Instant now = time.now();
		if (!before.isRetryable(now) || !repository.retryJob(id, now)) {
			throw new BusinessException(NotificationAdminErrorCode.RETRY_NOT_ALLOWED);
		}
		audit.record(new AuditLogCommand(actor.getId(), "NOTIFICATION_JOB_RETRY", "NOTIFICATION_JOB", id, null,
				Map.of("status", before.getStatus().name(), "attemptCount", before.getAttemptCount()),
				Map.of("status", "PENDING", "attemptBase", before.getAttemptCount()), null));
		AdminNotificationJob job = repository.findJob(id).orElseThrow(NotificationAdminService::notFound);
		return new AdminNotificationJobResult(job, job.isRetryable(now));
	}

	/** 이미 생성된 알림의 읽음·문구를 유지하고 모의 발송만 재접수한다. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public AdminNotification retryDelivery(User actor, UUID id) {
		requireAdmin(actor);
		AdminNotification before = repository.lockDelivery(id).orElseThrow(NotificationAdminService::notFound);
		if (!before.isRetryable() || !repository.retryDelivery(id, time.now())) {
			throw new BusinessException(NotificationAdminErrorCode.RETRY_NOT_ALLOWED);
		}
		audit.record(new AuditLogCommand(actor.getId(), "NOTIFICATION_DELIVERY_RETRY", "NOTIFICATION", id, null,
				Map.of("status", before.getMockDeliveryStatus().name(), "attemptCount", before.getDeliveryAttemptCount()),
				Map.of("status", "PENDING", "attemptBase", before.getDeliveryAttemptCount()), null));
		return repository.findDelivery(id).orElseThrow(NotificationAdminService::notFound);
	}

	/** DB 사용자 문맥의 활성 상태와 관리자 역할을 확인한다. */
	private static void requireAdmin(User actor) {
		if (actor == null || actor.getId() == null || actor.getRole() != UserRole.ADMIN || actor.getStatus() != UserStatus.ACTIVE) {
			throw new BusinessException(NotificationAdminErrorCode.ADMIN_REQUIRED);
		}
	}

	/** 작업 또는 알림 조회 부재를 공통 관리자 알림 오류로 표현한다. */
	private static BusinessException notFound() {
		return new BusinessException(NotificationAdminErrorCode.NOT_FOUND);
	}
}
