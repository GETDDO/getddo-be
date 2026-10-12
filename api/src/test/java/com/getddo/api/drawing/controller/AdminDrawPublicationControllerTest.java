package com.getddo.api.drawing.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.getddo.api.common.context.CurrentUserArgumentResolver;
import com.getddo.api.common.exception.GlobalExceptionHandler;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.repository.DrawPublicationRepository;
import com.getddo.core.drawing.service.*;
import com.getddo.core.user.domain.*;
import com.getddo.core.user.repository.UserRepository;
import com.getddo.core.user.service.UserService;

import static com.getddo.core.drawing.exception.DrawPublicationErrorCode.NOT_CONFIRMABLE;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 MVC 사용자 문맥·확인/권한 서비스를 사용한다. 공개 계산·저장소는 대역이며 DB 연동 검증은 아니다. */
class AdminDrawPublicationControllerTest {
	private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");
	private final UUID actorId = UUID.randomUUID(), eventId = UUID.randomUUID(), runId = UUID.randomUUID();
	private final DrawPublicationRepository repository = mock(DrawPublicationRepository.class);
	private final DrawPublicationService publications = mock(DrawPublicationService.class);
	private final UserRepository users = mock(UserRepository.class);
	private User actor;
	private DrawResultConfirmation confirmation;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		actor = user(UserRole.ADMIN, UserStatus.ACTIVE);
		confirmation = new DrawResultConfirmation(UUID.randomUUID(), runId, actorId, NOW);
		when(users.findById(actorId)).thenAnswer(call -> Optional.of(actor));
		when(repository.findEventId(runId)).thenReturn(Optional.of(eventId));
		when(publications.validateReplacement(eventId, runId)).thenReturn(run(null));
		when(repository.saveConfirmation(runId, actorId, NOW)).thenReturn(confirmation);
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mvc = MockMvcBuilders.standaloneSetup(new AdminDrawPublicationController(
				new DrawResultConfirmationService(repository, publications, users, new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC))),
				new DrawPublicationQueryService(publications, repository, users)))
				.setControllerAdvice(new GlobalExceptionHandler()).setValidator(validator)
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver(new UserService(users))).build();
	}

	@Test
	void preInitialConfirmationDoesNotClaimPublicationAndRetryKeepsOriginalActorAndTime() throws Exception {
		when(publications.publishConfirmed(eventId, runId)).thenReturn(Optional.empty());
		mvc.perform(selected(post("/api/v1/admin/draws/{id}/result-confirmation", runId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.confirmationId").value(confirmation.id().toString()))
				.andExpect(jsonPath("$.data.isPublished").value(false)).andExpect(jsonPath("$.data.publicationId").value(nullValue()))
				.andExpect(jsonPath("$.data.revision").value(nullValue())).andExpect(jsonPath("$.data.publishedAt").value(nullValue()));
		DrawResultConfirmation original = new DrawResultConfirmation(confirmation.id(), runId, UUID.randomUUID(), NOW.minusSeconds(20));
		when(publications.validateReplacement(eventId, runId)).thenReturn(run(original));
		mvc.perform(selected(post("/api/v1/admin/draws/{id}/result-confirmation", runId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.confirmedBy").value(original.confirmedBy().toString()))
				.andExpect(jsonPath("$.data.confirmedAt").value("2026-10-12T02:59:40Z"));
		verify(repository, times(1)).saveConfirmation(runId, actorId, NOW);
	}

	@Test
	void preInitialConfirmationCanReturnActualFirstPublication() throws Exception {
		DrawPublication first = publication(1);
		when(publications.publishConfirmed(eventId, runId)).thenReturn(Optional.of(first));
		mvc.perform(selected(post("/api/v1/admin/draws/{id}/result-confirmation", runId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.isPublished").value(true))
				.andExpect(jsonPath("$.data.publicationId").value(first.id().toString()))
				.andExpect(jsonPath("$.data.revision").value(1)).andExpect(jsonPath("$.data.publishedAt").value(NOW.toString()));
	}

	@Test
	void publicationRequiresInitialAndPreInitialRouteRejectsNewConfirmationAfterPublication() throws Exception {
		mvc.perform(selected(post("/api/v1/admin/draws/{id}/publication", runId)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PUBLICATION-005"));
		when(repository.findLatest(eventId)).thenReturn(Optional.of(publication(1)));
		mvc.perform(selected(post("/api/v1/admin/draws/{id}/result-confirmation", runId)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PUBLICATION-005"));
		verify(repository, never()).saveConfirmation(any(), any(), any());
		verify(publications, never()).publishConfirmed(any(), any());
	}

	@Test
	void publishedConfirmationRetryReturnsActualLatestVersionWithoutOverwritingCheck() throws Exception {
		DrawPublication latest = publication(3);
		when(repository.findLatest(eventId)).thenReturn(Optional.of(latest));
		when(publications.validateReplacement(eventId, runId)).thenReturn(run(confirmation));
		when(publications.publishConfirmed(eventId, runId)).thenReturn(Optional.of(latest));
		for (int attempt = 0; attempt < 2; attempt++) {
			mvc.perform(selected(post("/api/v1/admin/draws/{id}/publication", runId)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.data").value(aMapWithSize(7)))
					.andExpect(jsonPath("$.data.publicationId").value(latest.id().toString()))
					.andExpect(jsonPath("$.data.drawRunId").value(latest.sourceDrawId().toString()))
					.andExpect(jsonPath("$.data.revision").value(3));
		}
		mvc.perform(selected(post("/api/v1/admin/draws/{id}/result-confirmation", runId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(3));
		verify(repository, never()).saveConfirmation(any(), any(), any());
	}

	@Test
	void firstPostPublicationConfirmationUsesLinkedPublicationMetadata() throws Exception {
		DrawPublication first = publication(1), latest = publication(2);
		when(repository.findLatest(eventId)).thenReturn(Optional.of(first));
		when(publications.publishConfirmed(eventId, runId)).thenReturn(Optional.of(latest));
		mvc.perform(selected(post("/api/v1/admin/draws/{id}/publication", runId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(2))
				.andExpect(jsonPath("$.data.updatedBy").value(actorId.toString()));
		verify(repository).saveConfirmation(runId, actorId, NOW);
		verify(publications).publishConfirmed(eventId, runId);
	}

	@Test
	void historyReturnsOnlyActualChangesWithDefaultPageMetadataAndUtcTimes() throws Exception {
		DrawPublication latest = publication(2);
		UUID prize = UUID.randomUUID(), before = UUID.randomUUID(), after = UUID.randomUUID();
		DrawPublicationChange change = new DrawPublicationChange(latest, List.of(new DrawPublicationChange.SlotChange(
				prize, 1, UUID.randomUUID(), before, UUID.randomUUID(), after)));
		when(repository.eventExists(eventId)).thenReturn(true);
		when(publications.changes(eventId, new PageQuery(1, 20))).thenReturn(new PageResult<>(List.of(change), 1, 20, 2));
		mvc.perform(selected(get("/api/v1/admin/events/{id}/result-changes", eventId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data").value(aMapWithSize(4)))
				.andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.size").value(20))
				.andExpect(jsonPath("$.data.totalElements").value(2)).andExpect(jsonPath("$.data.items[0].revision").value(2))
				.andExpect(jsonPath("$.data.items[0].reason").value("연결 취소 사유"))
				.andExpect(jsonPath("$.data.items[0].updatedAt").value(NOW.toString()))
				.andExpect(jsonPath("$.data.items[0].changes[0].beforeUserId").value(before.toString()))
				.andExpect(jsonPath("$.data.items[0].changes[0].afterUserId").value(after.toString()));
	}

	@Test
	void customOutOfRangePageIsEmptyWithTotalPreserved() throws Exception {
		when(repository.eventExists(eventId)).thenReturn(true);
		when(publications.changes(eventId, new PageQuery(Integer.MAX_VALUE, 100)))
				.thenReturn(new PageResult<>(List.of(), Integer.MAX_VALUE, 100, 3));
		mvc.perform(selected(get("/api/v1/admin/events/{id}/result-changes", eventId)
				.param("page", "2147483647").param("size", "100")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.totalElements").value(3));
	}

	@ParameterizedTest
	@CsvSource({"page,0", "page,-1", "size,0", "size,101", "size,-1", "page,abc", "page,2147483648"})
	void invalidPaginationIs400(String name, String value) throws Exception {
		mvc.perform(selected(get("/api/v1/admin/events/{id}/result-changes", eventId).param(name, value)))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(repository, publications);
	}

	@ParameterizedTest
	@CsvSource({"USER,ACTIVE", "ADMIN,INACTIVE"})
	void everyEndpointRequiresActiveAdmin(UserRole role, UserStatus state) throws Exception {
		actor = user(role, state);
		for (MockHttpServletRequestBuilder request : endpoints()) {
			mvc.perform(selected(request)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PUBLICATION-003"));
		}
		verifyNoInteractions(repository, publications);
	}

	@Test
	void missingContextIs401AndRoleMismatchIs403() throws Exception {
		for (MockHttpServletRequestBuilder request : endpoints()) mvc.perform(request).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/admin/draws/{id}/publication", runId)
				.header("X-User-ID", actorId).header("X-User-Role", "USER")).andExpect(status().isForbidden());
		verifyNoInteractions(repository, publications);
	}

	@ParameterizedTest
	@ValueSource(strings = {"publication", "result-confirmation"})
	void missingRunIs404AndNonConfirmableIs409(String endpoint) throws Exception {
		when(repository.findEventId(runId)).thenReturn(Optional.empty());
		mvc.perform(selected(post("/api/v1/admin/draws/{id}/" + endpoint, runId))).andExpect(status().isNotFound());
		when(repository.findEventId(runId)).thenReturn(Optional.of(eventId));
		when(publications.validateReplacement(eventId, runId)).thenThrow(new BusinessException(NOT_CONFIRMABLE));
		mvc.perform(selected(post("/api/v1/admin/draws/{id}/" + endpoint, runId))).andExpect(status().isConflict());
		verify(repository, never()).saveConfirmation(any(), any(), any());
	}

	@Test
	void missingEventIs404AndMalformedPathIdsAre400() throws Exception {
		mvc.perform(selected(get("/api/v1/admin/events/{id}/result-changes", eventId))).andExpect(status().isNotFound());
		mvc.perform(selected(get("/api/v1/admin/events/bad/result-changes"))).andExpect(status().isBadRequest());
		mvc.perform(selected(post("/api/v1/admin/draws/bad/publication"))).andExpect(status().isBadRequest());
		mvc.perform(selected(post("/api/v1/admin/draws/bad/result-confirmation"))).andExpect(status().isBadRequest());
	}

	private MockHttpServletRequestBuilder selected(MockHttpServletRequestBuilder request) {
		return request.header("X-User-ID", actorId).header("X-User-Role", actor.role());
	}
	private List<MockHttpServletRequestBuilder> endpoints() {
		return List.of(post("/api/v1/admin/draws/{id}/publication", runId),
				post("/api/v1/admin/draws/{id}/result-confirmation", runId), get("/api/v1/admin/events/{id}/result-changes", eventId));
	}
	private User user(UserRole role, UserStatus status) {
		return new User(actorId, "관리자", role, status, null, null, null, null, null, NOW, NOW);
	}
	private DrawPublicationRun run(DrawResultConfirmation check) {
		return new DrawPublicationRun(runId, eventId, 2, DrawRunStatus.CONFIRMED, NOW, List.of(), check);
	}
	private DrawPublication publication(int revision) {
		return new DrawPublication(UUID.randomUUID(), eventId, UUID.randomUUID(), revision,
				revision == 1 ? DrawPublicationType.INITIAL : DrawPublicationType.UPDATE,
				revision == 1 ? null : "연결 취소 사유", revision == 1 ? null : actorId,
				revision == 1 ? null : NOW, NOW, List.of());
	}
}
