package com.getddo.core.notification.service;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.getddo.core.audit.service.AuditLogService;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.notification.domain.AdminNotificationJob;
import com.getddo.core.notification.domain.AdminNotificationJobResult;
import com.getddo.core.notification.domain.NotificationJobQuery;
import com.getddo.core.notification.domain.NotificationJobStatus;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.repository.NotificationAdminRepository;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationAdminServiceTest {
	private static final Instant NOW = Instant.parse("2026-10-12T01:00:00Z");
	private final NotificationAdminRepository repository = mock(NotificationAdminRepository.class);
	private final AuditLogService audit = mock(AuditLogService.class);
	private final TimeProvider time = mock(TimeProvider.class);
	private NotificationAdminService service;

	/** 테스트 데이터와 고정 시계를 준비한다. */
	@BeforeEach
	void setUp() {
		when(time.now()).thenReturn(NOW);
		service = new NotificationAdminService(repository, audit, time);
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

	/** 조회 페이지 전체에 Service에서 읽은 동일한 기준 시각을 적용한다. */
	@Test
	@DisplayName("작업 목록의 복구 가능 여부는 Service 시각으로 한 번에 판단한다")
	void evaluatesPageUsingOneServiceTime() {
		// given
		AdminNotificationJob future = scheduledJob(NOW.plusNanos(1));
		AdminNotificationJob started = scheduledJob(NOW);
		NotificationJobQuery query = new NotificationJobQuery(1, 20, null, null, null, null, null);
		when(repository.findJobs(query)).thenReturn(new PageResult<>(List.of(future, started), 1, 20, 2));
		// when
		PageResult<AdminNotificationJobResult> result = service.findJobs(actor(UserRole.ADMIN, UserStatus.ACTIVE), query);
		// then
		assertThat(result.getItems()).extracting(AdminNotificationJobResult::isRetryable).containsExactly(true, false);
		assertThat(result.getItems()).extracting(AdminNotificationJobResult::getJob).containsExactly(future, started);
		assertThat(result.getTotalElements()).isEqualTo(2);
		verify(time, times(1)).now();
	}

	/** 상세 조회의 시작 시각 경계도 Controller가 아닌 Service에서 판단한다. */
	@Test
	@DisplayName("작업 상세는 Service 현재 시각이 시작 시각에 도달하면 복구 불가를 반환한다")
	void evaluatesDetailAtStartBoundary() {
		// given
		AdminNotificationJob job = scheduledJob(NOW.plusSeconds(1));
		User admin = actor(UserRole.ADMIN, UserStatus.ACTIVE);
		when(repository.findJob(job.getId())).thenReturn(Optional.of(job));
		// when / then
		assertThat(service.findJob(admin, job.getId()).isRetryable()).isTrue();
		when(time.now()).thenReturn(NOW.plusSeconds(1));
		assertThat(service.findJob(admin, job.getId()).isRetryable()).isFalse();
	}

	/** 복구 여부의 시간 경계를 검증할 시작 안내 작업을 만든다. */
	private AdminNotificationJob scheduledJob(Instant startsAt) {
		return new AdminNotificationJob(UUID.randomUUID(), UUID.randomUUID(), null, NotificationType.EVENT_START,
				NotificationJobStatus.FAILED, 4, 0, NOW, null, NOW, null, null, null, null, null,
				null, EventStatus.SCHEDULED, startsAt, false);
	}

	private User actor(UserRole role, UserStatus status) {
		return new User(UUID.randomUUID(), "운영자", role, status, null, null, null, null, null, null, null);
	}
}
