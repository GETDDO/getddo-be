package com.getddo.core.audit.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getddo.core.audit.domain.AuditLogEntry;
import com.getddo.core.audit.domain.AuditLogQuery;
import com.getddo.core.audit.exception.AuditQueryErrorCode;
import com.getddo.core.audit.repository.AuditLogQueryRepository;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuditLogQueryServiceTest {
	private final AuditLogQueryRepository repository = mock(AuditLogQueryRepository.class);
	private final AuditLogQueryService service = new AuditLogQueryService(repository);

	@ParameterizedTest
	@CsvSource({"USER,ACTIVE", "USER,INACTIVE", "ADMIN,INACTIVE"})
	@DisplayName("활성 관리자 외에는 목록과 상세 저장소를 조회하지 않는다")
	void rejectsUnqualifiedActor(UserRole role, UserStatus status) {
		// given
		User actor = actor(role, status);
		// when / then
		assertDenied(actor);
		verifyNoInteractions(repository);
	}

	@Test
	@DisplayName("문맥이 없으면 두 조회를 모두 AUDIT-002로 거절한다")
	void rejectsMissingActor() {
		// given / when / then
		assertDenied(null);
		verifyNoInteractions(repository);
	}

	@Test
	@DisplayName("목록과 상세는 원본을 노출하지 않고 페이지 정보와 공개 필드를 보존한다")
	void sanitizesBothReadPaths() {
		// given
		User actor = actor(UserRole.ADMIN, UserStatus.ACTIVE);
		AuditLogEntry raw = new AuditLogEntry(UUID.randomUUID(), null, "EVENT_CANCEL", "EVENT", UUID.randomUUID(),
				null, null, Instant.parse("2026-10-15T00:00:00Z"),
				Map.of("status", "OPEN", "token", "fixture-secret"), null);
		AuditLogQuery query = query();
		when(repository.findAll(query)).thenReturn(new PageResult<>(List.of(raw), 2, 20, 21));
		when(repository.findById(raw.getId())).thenReturn(Optional.of(raw));
		// when
		PageResult<AuditLogEntry> page = service.findAll(actor, query);
		AuditLogEntry detail = service.findById(actor, raw.getId());
		// then
		assertThat(page.getPage()).isEqualTo(2);
		assertThat(page.getTotalElements()).isEqualTo(21);
		assertThat(page.getItems().getFirst().getBeforeData()).containsOnlyKeys("status");
		assertThat(detail.getBeforeData()).containsOnlyKeys("status");
		assertThat(raw.getBeforeData()).containsKey("token");
	}

	@Test
	@DisplayName("없는 감사 상세는 AUDIT-003이다")
	void reportsMissingDetail() {
		// given
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.empty());
		// when / then
		assertError(() -> service.findById(actor(UserRole.ADMIN, UserStatus.ACTIVE), id), AuditQueryErrorCode.NOT_FOUND);
	}

	private void assertDenied(User actor) {
		assertError(() -> service.findAll(actor, query()), AuditQueryErrorCode.ADMIN_REQUIRED);
		assertError(() -> service.findById(actor, UUID.randomUUID()), AuditQueryErrorCode.ADMIN_REQUIRED);
	}

	private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, AuditQueryErrorCode code) {
		assertThatExceptionOfType(BusinessException.class).isThrownBy(call)
				.satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(code));
	}

	private static AuditLogQuery query() {
		return new AuditLogQuery(2, 20, null, null, null, null, null, null);
	}

	private static User actor(UserRole role, UserStatus status) {
		return new User(UUID.randomUUID(), "감사 조회자", role, status, null,
				null, null, null, null, null, null);
	}
}
