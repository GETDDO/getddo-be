package com.getddo.api.drawing.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.getddo.api.common.context.CurrentUserArgumentResolver;
import com.getddo.api.common.exception.GlobalExceptionHandler;
import com.getddo.core.audit.service.AuditLogService;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.repository.*;
import com.getddo.core.drawing.service.*;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.user.domain.*;
import com.getddo.core.user.repository.UserRepository;
import com.getddo.core.user.service.UserService;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminDrawResumeControllerTest {
	private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");
	private final UUID runId = UUID.randomUUID(), eventId = UUID.randomUUID();
	private final UUID adminId = UUID.randomUUID(), userId = UUID.randomUUID(), inactiveAdminId = UUID.randomUUID();
	private final DrawResultRepository results = mock(DrawResultRepository.class);
	private final DrawSnapshotRepository snapshots = mock(DrawSnapshotRepository.class);
	private final DrawExecutionRepository executions = mock(DrawExecutionRepository.class);
	private final DrawExecutionAttemptService attempts = mock(DrawExecutionAttemptService.class);
	private final DrawExecutionPreparationService preparations = mock(DrawExecutionPreparationService.class);
	private MockMvc mvc;

	/** 실제 사용자 문맥과 공통 실행 흐름을 사용하는 MVC 환경을 준비하고 영속성·확정 의존성을 대역한다. */
	@BeforeEach void setUp() {
		Map<UUID, User> users = Map.of(adminId, user(adminId, UserRole.ADMIN, UserStatus.ACTIVE),
				userId, user(userId, UserRole.USER, UserStatus.ACTIVE), inactiveAdminId, user(inactiveAdminId, UserRole.ADMIN, UserStatus.INACTIVE));
		UserRepository userRepository = id -> Optional.ofNullable(users.get(id));
		TimeProvider time = new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC));
		DrawExecutionControlService control = new DrawExecutionControlService(results, snapshots, executions,
				mock(DrawFailureRepository.class), userRepository, mock(AuditLogService.class), time);
		DrawExecutionService service = new DrawExecutionService(control, attempts, preparations, time);
		mvc = MockMvcBuilders.standaloneSetup(new AdminDrawResumeController(service))
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver(new UserService(userRepository)))
				.setControllerAdvice(new GlobalExceptionHandler()).build();
		when(results.findEventId(runId)).thenReturn(Optional.of(eventId));
		when(snapshots.lockEvent(eventId)).thenReturn(Optional.of(new DrawSnapshotSource.Event(eventId,
				EventType.NO_TICKET, false, NOW.minusSeconds(300), EventStatus.CLOSED, false, "vip", List.of())));
		when(attempts.stored(any())).thenAnswer(invocation -> {
			DrawExecutionState state = invocation.getArgument(0);
			DrawConfirmation confirmation = state.getStatus() == DrawRunStatus.CONFIRMED
					? new DrawConfirmation(runId, eventId, NOW, List.of()) : null;
			return new DrawExecutionResult(runId, state.getStatus(), confirmation);
		});
	}

	/** 확정된 최초 실행은 200과 저장 결과를 반환한다. */
	@Test @DisplayName("확정된 최초 실행은 200과 저장 결과를 반환한다")
	void returnsStoredConfirmation() throws Exception {
		// given
		when(executions.lockRun(runId)).thenReturn(Optional.of(state(DrawType.INITIAL, DrawRunStatus.CONFIRMED)));
		// when / then
		mvc.perform(post(path()).header("X-User-ID", adminId).header("X-User-Role", "ADMIN"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.runId").value(runId.toString()))
				.andExpect(jsonPath("$.data.status").value("CONFIRMED")).andExpect(jsonPath("$.data.confirmation.results").isArray());
		verifyNoInteractions(preparations);
		verify(attempts, never()).execute(any(), any());
	}

	/** 진행 중 최초 실행은 202로 현재 상태만 반환한다. */
	@Test @DisplayName("진행 중 최초 실행은 202로 현재 상태만 반환한다")
	void returnsInProgressWithoutReselecting() throws Exception {
		// given
		when(executions.lockRun(runId)).thenReturn(Optional.of(state(DrawType.INITIAL, DrawRunStatus.RUNNING)));
		// when / then
		mvc.perform(post(path()).header("X-User-ID", adminId).header("X-User-Role", "ADMIN"))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("RUNNING"))
				.andExpect(jsonPath("$.data.confirmation").value(nullValue()));
		verifyNoInteractions(preparations);
	}

	/** 최초 재개 API는 재추첨 실행을 409로 거절한다. */
	@Test @DisplayName("최초 재개 API는 재추첨 실행을 409로 거절한다")
	void rejectsReplacement() throws Exception {
		// given
		when(executions.lockRun(runId)).thenReturn(Optional.of(state(DrawType.REPLACEMENT, DrawRunStatus.CONFIRMED)));
		// when / then
		mvc.perform(post(path()).header("X-User-ID", adminId).header("X-User-Role", "ADMIN"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRAW-005"));
		verifyNoInteractions(attempts, preparations);
	}

	/** 비관리자·비활성 관리자는 실행 상태를 변경하기 전에 403으로 거절한다. */
	@Test @DisplayName("비관리자·비활성 관리자는 실행 상태를 변경하기 전에 403으로 거절한다")
	void rejectsUnauthorizedActors() throws Exception {
		// given / when / then
		mvc.perform(post(path()).header("X-User-ID", userId).header("X-User-Role", "USER").header("X-User-Membership", "vip"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DRAW-006"));
		mvc.perform(post(path()).header("X-User-ID", inactiveAdminId).header("X-User-Role", "ADMIN"))
				.andExpect(status().isForbidden());
		verifyNoInteractions(results, executions, attempts, preparations);
	}

	/** 잘못된 실행 UUID는 400, 사용자 문맥 누락은 401로 거절한다. */
	@Test @DisplayName("잘못된 실행 UUID는 400, 사용자 문맥 누락은 401로 거절한다")
	void rejectsMalformedOrMissingInput() throws Exception {
		// given / when / then
		mvc.perform(post("/api/v1/admin/draws/not-a-uuid/resume").header("X-User-ID", adminId).header("X-User-Role", "ADMIN"))
				.andExpect(status().isBadRequest());
		mvc.perform(post(path())).andExpect(status().isUnauthorized());
		verifyNoInteractions(results, executions, attempts, preparations);
	}
	/** 현재 테스트 실행 ID의 최초 재개 경로를 구성한다. */
	private String path() { return "/api/v1/admin/draws/" + runId + "/resume"; }
	/** HTTP 권한·상태 분기를 검증할 종류별 실행 상태를 구성한다. */
	private DrawExecutionState state(DrawType type, DrawRunStatus status) {
		return new DrawExecutionState(runId, eventId, type, status, NOW.minusSeconds(1),
				status == DrawRunStatus.RUNNING ? UUID.randomUUID() : null,
				status == DrawRunStatus.RUNNING ? NOW.plusSeconds(300) : null, null, NOW.minusSeconds(1));
	}
	/** 역할·활성 상태가 서로 다른 사용자 문맥을 구성한다. */
	private User user(UUID id, UserRole role, UserStatus status) {
		return new User(id, "검증 사용자", role, status, Membership.VIP, null, null, null, null, NOW, NOW);
	}
}
