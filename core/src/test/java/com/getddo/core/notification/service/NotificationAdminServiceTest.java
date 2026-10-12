package com.getddo.core.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.getddo.core.audit.service.AuditLogService;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.notification.domain.AdminNotificationJob;
import com.getddo.core.notification.domain.NotificationJobQuery;
import com.getddo.core.notification.domain.NotificationJobStatus;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.repository.NotificationAdminRepository;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationAdminServiceTest {
	private static final Instant NOW = Instant.parse("2026-10-12T01:00:00Z");
	private final NotificationAdminRepository repository = mock(NotificationAdminRepository.class);
	private final AuditLogService audit = mock(AuditLogService.class);
	private NotificationAdminService service;

	/** 테스트 데이터와 고정 시계를 준비한다. */
	@BeforeEach
	void setUp() {
		service = new NotificationAdminService(repository, audit, new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
	}

	/** 일반 사용자는 상태와 관계없이 관리자 조회를 할 수 없다 */
	@ParameterizedTest
	@EnumSource(UserStatus.class)
	@DisplayName("일반 사용자는 상태와 관계없이 관리자 조회를 할 수 없다")
	void rejectsRegularUsers(UserStatus status) {
		// given / when / then
		assertThatThrownBy(() -> service.findJobs(actor(UserRole.USER, status), new NotificationJobQuery(1, 20, null, null, null, null, null)))
				.isInstanceOf(BusinessException.class);
		verifyNoInteractions(repository, audit);
	}

	/** 비활성 관리자와 없는 대상의 요청을 구분한다 */
	@Test
	@DisplayName("비활성 관리자와 없는 대상의 요청을 구분한다")
	void rejectsInactiveAndMissing() {
		// given / when / then
		assertThatThrownBy(() -> service.findJob(actor(UserRole.ADMIN, UserStatus.INACTIVE), UUID.randomUUID()))
				.hasMessageContaining("활성 관리자");
		when(repository.lockJob(any())).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.retryJob(actor(UserRole.ADMIN, UserStatus.ACTIVE), UUID.randomUUID()))
				.hasMessageContaining("찾을 수 없습니다");
		verifyNoInteractions(audit);
	}

	/** 복구 접수 후 감사 저장 실패를 숨기지 않고 트랜잭션에 전파한다 */
	@Test
	@DisplayName("복구 접수 후 감사 저장 실패를 숨기지 않고 트랜잭션에 전파한다")
	void propagatesAuditFailure() {
		// given
		UUID id = UUID.randomUUID();
		AdminNotificationJob before = new AdminNotificationJob(id, null, null, NotificationType.RESULT_PUBLISHED,
				NotificationJobStatus.FAILED, 4, 0, NOW, null, NOW, null, null, "NOTIFICATION-005", null, null,
				null, null, null, false);
		when(repository.lockJob(id)).thenReturn(Optional.of(before));
		when(repository.retryJob(id, NOW)).thenReturn(true);
		when(audit.record(any())).thenThrow(new IllegalStateException("audit failure"));
		// when / then
		assertThatThrownBy(() -> service.retryJob(actor(UserRole.ADMIN, UserStatus.ACTIVE), id)).isInstanceOf(IllegalStateException.class);
	}

	private User actor(UserRole role, UserStatus status) {
		return new User(UUID.randomUUID(), "운영자", role, status, null, null, null, null, null, null, null);
	}
}
