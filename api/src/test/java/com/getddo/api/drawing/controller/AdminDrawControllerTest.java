package com.getddo.api.drawing.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.getddo.api.common.context.CurrentUserArgumentResolver;
import com.getddo.api.common.exception.GlobalExceptionHandler;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.repository.*;
import com.getddo.core.drawing.service.DrawInspectionService;
import com.getddo.core.user.domain.*;
import com.getddo.core.user.service.UserService;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 Controller·사용자 문맥·업무 서비스와 저장소 대역을 연결한 API 테스트다. */
class AdminDrawControllerTest {
	private final DrawInspectionRepository repository = mock(DrawInspectionRepository.class);
	private final DrawCheckRepository checks = mock(DrawCheckRepository.class);
	private final UUID actorId = UUID.randomUUID(), runId = UUID.randomUUID(), eventId = UUID.randomUUID();
	private final Instant now = Instant.parse("2026-10-12T01:00:00Z");
	private User selected;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		selected = user(UserRole.ADMIN, UserStatus.ACTIVE);
		DrawInspectionService service = new DrawInspectionService(repository, checks, new TimeProvider(Clock.fixed(now, ZoneOffset.UTC)));
		mvc = MockMvcBuilders.standaloneSetup(new AdminDrawController(service))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver(new UserService(id -> Optional.of(selected).filter(user -> user.getId().equals(id)))))
				.build();
		when(repository.findRun(runId)).thenReturn(Optional.of(run()));
	}

	@Test
	@DisplayName("실행 목록은 승인된 drawType·공통 페이징·이벤트 소속을 반환한다")
	void listsRunsWithPaging() throws Exception {
		// given
		when(repository.eventExists(eventId)).thenReturn(true);
		when(repository.findRuns(eventId, new PageQuery(2, 1))).thenReturn(new PageResult<>(List.of(run()), 2, 1, 2));
		// when / then
		mvc.perform(get("/api/v1/admin/events/{id}/draws", eventId).header("X-User-ID", actorId).header("X-User-Role", selected.getRole().name()).param("page", "2").param("size", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(2))
				.andExpect(jsonPath("$.data.items[0].drawType").value("INITIAL"))
				.andExpect(jsonPath("$.data.items[0].executionType").doesNotExist());
	}

	@Test
	@DisplayName("상세는 실제 공개 이력과 취소를 각각 반환하고 미연결 실패 요약을 만들지 않는다")
	void detailSeparatesPublicationAndCancellation() throws Exception {
		// given
		UUID winnerId = UUID.randomUUID();
		when(repository.findResults(runId)).thenReturn(List.of(new DrawResultView(UUID.randomUUID(), runId,
				UUID.randomUUID(), eventId, 1, 1, DrawSelectionResultType.SELECTED, UUID.randomUUID(), winnerId, true, true)));
		// when / then
		mvc.perform(get("/api/v1/admin/draws/{id}", runId).header("X-User-ID", actorId).header("X-User-Role", selected.getRole().name()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.results[0].wasPublished").value(true))
				.andExpect(jsonPath("$.data.results[0].isCanceled").value(true))
				.andExpect(jsonPath("$.data.results[0].userId").value(winnerId.toString()))
				.andExpect(jsonPath("$.data.failureCount").doesNotExist()).andExpect(jsonPath("$.data.cancellations").doesNotExist());
	}

	@Test
	@DisplayName("불일치 검증은 201과 passed=false를 반환하며 검증 이력 조회가 가능하다")
	void storesAndListsFailedVerification() throws Exception {
		// given
		when(repository.findAllCandidates(runId)).thenReturn(List.of());
		when(repository.findResults(runId)).thenReturn(List.of());
		DrawVerification stored = new DrawVerification(UUID.randomUUID(), runId, false,
				List.of(new DrawCheckItem("RESULTS", false, "저장 결과 불일치")), now, actorId);
		when(checks.save(eq(runId), eq(actorId), eq(now), eq(false), anyList())).thenReturn(stored);
		when(checks.findByRun(runId, new PageQuery(1, 20))).thenReturn(new PageResult<>(List.of(stored), 1, 20, 1));
		// when / then
		mvc.perform(post("/api/v1/admin/draws/{id}/checks", runId).header("X-User-ID", actorId).header("X-User-Role", selected.getRole().name()))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.passed").value(false))
				.andExpect(jsonPath("$.data.verifiedBy").value(actorId.toString()));
		mvc.perform(get("/api/v1/admin/draws/{id}/checks", runId).header("X-User-ID", actorId).header("X-User-Role", selected.getRole().name()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(stored.getId().toString()));
	}

	@Test
	@DisplayName("응모자 조회는 요청한 실행의 페이지를 반환한다")
	void candidatesUseRequestedRun() throws Exception {
		// given
		when(repository.findCandidates(runId, new PageQuery(1, 20))).thenReturn(new PageResult<>(List.of(), 1, 20, 0));
		// when / then
		mvc.perform(get("/api/v1/admin/draws/{id}/participants", runId).header("X-User-ID", actorId).header("X-User-Role", selected.getRole().name())).andExpect(status().isOk());
		verify(repository).findCandidates(runId, new PageQuery(1, 20));
	}

	@Test
	@DisplayName("재추첨 상세는 버전 2의 최초 실행·실제 빈 자리·유지 배정을 반환한다")
	void replacementDetailIncludesStoredTargetsAndRetainedAwards() throws Exception {
		// given
		UUID firstId = UUID.randomUUID(), prizeId = UUID.randomUUID(), awardId = UUID.randomUUID(), winnerId = UUID.randomUUID();
		DrawRunView replacement = new DrawRunView(runId, eventId, 1, DrawType.REPLACEMENT, DrawRunStatus.CONFIRMED, firstId,
				"weighted-without-replacement-v1", new DrawSnapshot.Rules(2, false, "NO_TICKET", "ALL", 1, 3, 5,
				List.of(new DrawSnapshotSource.Prize(prizeId, 1, 3))), now, now, now, now,
				new DrawReplacementRulesView(firstId, List.of(new DrawReplacementRulesView.Slot(prizeId, 1, 3)),
				List.of(new DrawReplacementRulesView.RetainedAward(awardId, winnerId, prizeId, 1))));
		when(repository.findRun(runId)).thenReturn(Optional.of(replacement));
		when(repository.findAllCandidates(runId)).thenReturn(List.of());
		when(repository.findResults(runId)).thenReturn(List.of());
		// when / then
		mvc.perform(get("/api/v1/admin/draws/{id}", runId).header("X-User-ID", actorId).header("X-User-Role", selected.getRole().name()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.drawType").value("REPLACEMENT"))
				.andExpect(jsonPath("$.data.rulesSnapshot.version").value(2))
				.andExpect(jsonPath("$.data.rulesSnapshot.originalRunId").value(firstId.toString()))
				.andExpect(jsonPath("$.data.rulesSnapshot.targetSlots[0].slotNumber").value(3))
				.andExpect(jsonPath("$.data.rulesSnapshot.retainedAwards[0].resultId").value(awardId.toString()))
				.andExpect(jsonPath("$.data.rulesSnapshot.retainedAwards[0].userId").value(winnerId.toString()));
	}

	@ParameterizedTest
	@CsvSource({"0,20", "1,0", "1,101", "-1,20"})
	@DisplayName("허용 범위 밖 페이지는 공통 400 응답으로 거절한다")
	void rejectsInvalidPage(int page, int size) throws Exception {
		// given / when / then
		mvc.perform(get("/api/v1/admin/draws/{id}/checks", runId).header("X-User-ID", actorId).header("X-User-Role", selected.getRole().name())
				.param("page", Integer.toString(page)).param("size", Integer.toString(size))).andExpect(status().isBadRequest());
		verifyNoInteractions(checks);
	}

	@Test
	@DisplayName("일반 사용자와 비활성 관리자는 모든 조회·검증 경로에서 403이다")
	void rejectsUnauthorizedUsers() throws Exception {
		// given / when / then
		for (User user : List.of(user(UserRole.USER, UserStatus.ACTIVE), user(UserRole.ADMIN, UserStatus.INACTIVE))) {
			selected = user;
			mvc.perform(get("/api/v1/admin/events/{id}/draws", eventId).header("X-User-ID", actorId).header("X-User-Role", selected.getRole().name())).andExpect(status().isForbidden());
			mvc.perform(get("/api/v1/admin/draws/{id}", runId).header("X-User-ID", actorId).header("X-User-Role", selected.getRole().name())).andExpect(status().isForbidden());
			mvc.perform(get("/api/v1/admin/draws/{id}/participants", runId).header("X-User-ID", actorId).header("X-User-Role", selected.getRole().name())).andExpect(status().isForbidden());
			mvc.perform(get("/api/v1/admin/draws/{id}/checks", runId).header("X-User-ID", actorId).header("X-User-Role", selected.getRole().name())).andExpect(status().isForbidden());
			mvc.perform(post("/api/v1/admin/draws/{id}/checks", runId).header("X-User-ID", actorId).header("X-User-Role", selected.getRole().name())).andExpect(status().isForbidden());
		}
		verifyNoInteractions(checks);
	}

	@Test
	@DisplayName("사용자 헤더 누락·잘못된 UUID·없는 실행을 구분한다")
	void validatesContextAndIdentifiers() throws Exception {
		// given / when / then
		mvc.perform(get("/api/v1/admin/draws/{id}", runId)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/admin/draws/not-a-uuid").header("X-User-ID", actorId).header("X-User-Role", selected.getRole().name())).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/admin/draws/{id}", UUID.randomUUID()).header("X-User-ID", actorId).header("X-User-Role", selected.getRole().name())).andExpect(status().isNotFound());
	}

	private DrawRunView run() {
		return new DrawRunView(runId, eventId, 0, DrawType.INITIAL, DrawRunStatus.CONFIRMED, null, null, null, now, now, now, now);
	}
	private User user(UserRole role, UserStatus status) {
		return new User(actorId, "관리자", role, status, null, null, null, null, null, now, now);
	}
}
