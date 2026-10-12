package com.getddo.api.drawing.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.getddo.api.common.context.CurrentUserArgumentResolver;
import com.getddo.api.common.exception.GlobalExceptionHandler;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.service.AwardCancellationService;
import com.getddo.core.user.domain.*;
import com.getddo.core.user.service.UserService;

import static com.getddo.core.drawing.exception.AwardCancellationErrorCode.*;
import static com.getddo.core.drawing.exception.DrawingErrorCode.ADMIN_REQUIRED;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminAwardCancellationControllerTest {
	private final UUID actor = UUID.randomUUID(), win = UUID.randomUUID(), key = UUID.randomUUID(), run = UUID.randomUUID();
	private final UUID cancellationId = UUID.randomUUID();
	private final AwardCancellationService service = mock(AwardCancellationService.class);
	private MockMvc mvc;

	@BeforeEach void setUp() {
		User admin = new User(actor, "관리자", UserRole.ADMIN, UserStatus.ACTIVE, Membership.VIP,
				null, null, null, null, Instant.EPOCH, Instant.EPOCH);
		mvc = MockMvcBuilders.standaloneSetup(new AdminAwardCancellationController(service))
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver(new UserService(id -> Optional.of(admin))))
				.setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@Test @DisplayName("AD05 미확정 응답은 202와 취소·연결 실행 ID를 제공하고 실제 관리자만 전달한다")
	void acceptsCancellationAndIgnoresBodyActor() throws Exception {
		// given
		when(service.cancel(actor, win, key, "사유")).thenReturn(receipt(DrawRunStatus.PREPARING));
		// when / then
		mvc.perform(post(cancelPath()).header("X-User-ID", actor).header("X-User-Role", "ADMIN")
				.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\" 사유 \",\"actorId\":\"" + UUID.randomUUID() + "\",\"role\":\"USER\"}"))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$.data.cancellationId").value(cancellationId.toString()))
				.andExpect(jsonPath("$.data.replacementDrawRunId").value(run.toString()));
		verify(service).cancel(actor, win, key, "사유");
	}

	@Test @DisplayName("AD05 확정 재요청은 200으로 연결 실행 상태를 반환한다")
	void returnsConfirmedCancellation() throws Exception {
		// given
		when(service.cancel(actor, win, key, "사유")).thenReturn(receipt(DrawRunStatus.CONFIRMED));
		// when / then
		mvc.perform(post(cancelPath()).header("X-User-ID", actor).header("X-User-Role", "ADMIN")
				.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"사유\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
	}

	@Test @DisplayName("사유 누락·공백·초과 또는 키 누락·형식 오류는 Service를 호출하지 않는다")
	void rejectsInvalidBodyAndKey() throws Exception {
		// given / when / then
		for (String body : List.of("{}", "{\"reason\":\" \"}", "{\"reason\":\"" + "가".repeat(1001) + "\"}")) {
			mvc.perform(post(cancelPath()).header("X-User-ID", actor).header("X-User-Role", "ADMIN")
					.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
		}
		mvc.perform(post(cancelPath()).header("X-User-ID", actor).header("X-User-Role", "ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"사유\"}")).andExpect(status().isBadRequest());
		mvc.perform(post(cancelPath()).header("X-User-ID", actor).header("X-User-Role", "ADMIN")
				.header("Idempotency-Key", "invalid").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"사유\"}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test @DisplayName("권한·대상·멱등 충돌 업무 오류는 403·404·409 공통 응답으로 변환한다")
	void mapsBusinessErrors() throws Exception {
		// given / when / then
		for (com.getddo.core.common.exception.ErrorCode code : List.of(ADMIN_REQUIRED, NOT_FOUND, KEY_CONFLICT, NOT_CANCELABLE)) {
			doThrow(new BusinessException(code)).when(service).cancel(actor, win, key, "사유");
			mvc.perform(post(cancelPath()).header("X-User-ID", actor).header("X-User-Role", "ADMIN")
					.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"사유\"}"))
					.andExpect(status().is(code.getStatus())).andExpect(jsonPath("$.code").value(code.getCode()));
		}
	}

	@Test @DisplayName("AD09 진행 상태는 202, 확정 결과는 저장 결과 ID와 함께 200으로 반환한다")
	void returnsExistingRunAndSavedResults() throws Exception {
		// given
		when(service.startOrResume(actor, cancellationId)).thenReturn(new DrawExecutionResult(run, DrawRunStatus.RUNNING, null));
		// when / then
		mvc.perform(post(redrawPath()).header("X-User-ID", actor).header("X-User-Role", "ADMIN"))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$.data.runId").value(run.toString()));
		DrawResult result = new DrawResult(win, DrawSelection.unfilled(new DrawPrize(UUID.randomUUID(), 1, 1), 1), Instant.EPOCH);
		when(service.startOrResume(actor, cancellationId)).thenReturn(new DrawExecutionResult(run, DrawRunStatus.CONFIRMED,
				new DrawConfirmation(run, UUID.randomUUID(), Instant.EPOCH, List.of(result))));
		mvc.perform(post(redrawPath()).header("X-User-ID", actor).header("X-User-Role", "ADMIN"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.confirmation.results[0].resultId").value(win.toString()));
	}

	@Test @DisplayName("사용자 문맥 누락과 DB 역할에 맞지 않는 헤더는 Service 호출 전에 거절한다")
	void rejectsMissingAndForgedContext() throws Exception {
		// given / when / then
		mvc.perform(post(redrawPath())).andExpect(status().isUnauthorized());
		mvc.perform(post(redrawPath()).header("X-User-ID", actor).header("X-User-Role", "USER"))
				.andExpect(status().isForbidden());
		verifyNoInteractions(service);
	}

	private AwardCancellationReceipt receipt(DrawRunStatus status) {
		return new AwardCancellationReceipt(new AwardCancellation(cancellationId, win, run, actor, "사유", Instant.EPOCH, key),
				new DrawExecutionResult(run, status, status == DrawRunStatus.CONFIRMED
						? new DrawConfirmation(run, UUID.randomUUID(), Instant.EPOCH, List.of()) : null));
	}
	private String cancelPath() { return "/api/v1/admin/wins/" + win + "/cancellations"; }
	private String redrawPath() { return "/api/v1/admin/cancellations/" + cancellationId + "/redraws"; }
}
