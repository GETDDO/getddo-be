package com.getddo.core.drawing.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.*;
import static com.getddo.core.drawing.exception.DrawingErrorCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DrawExecutionServiceTest {
	private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");
	private final UUID runId = UUID.randomUUID(), eventId = UUID.randomUUID(), attemptId = UUID.randomUUID();
	private final DrawExecutionControlService control = mock(DrawExecutionControlService.class);
	private final DrawExecutionAttemptService attempts = mock(DrawExecutionAttemptService.class);
	private final DrawExecutionPreparationService preparations = mock(DrawExecutionPreparationService.class);
	private final DrawExecutionService service = new DrawExecutionService(control, attempts, preparations,
			new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)));

	@Test @DisplayName("복구 결과의 거절은 복구 서비스 반환 뒤 처리하고 실패를 다시 기록하지 않는다")
	void rejectsAfterRecoveryHasReturned() {
		// given
		DrawExecutionState failed = state(DrawRunStatus.FAILED);
		when(control.claim(runId, null, false, false)).thenReturn(new DrawExecutionClaim(failed, false, NOT_EXECUTABLE));
		// when / then
		assertThatThrownBy(() -> service.execute(runId)).isInstanceOfSatisfying(BusinessException.class,
				failure -> assertThat(failure.getErrorCode()).isSameAs(NOT_EXECUTABLE));
		verifyNoInteractions(attempts, preparations);
		verify(control, never()).recordFailure(any(), any(), any(), any());
	}

	@Test @DisplayName("확정 실행은 저장 결과를 반환하고 준비·선정을 다시 호출하지 않는다")
	void replaysConfirmedResult() {
		// given
		DrawExecutionState confirmed = state(DrawRunStatus.CONFIRMED);
		DrawConfirmation stored = new DrawConfirmation(runId, eventId, NOW, List.of());
		DrawExecutionResult result = new DrawExecutionResult(runId, DrawRunStatus.CONFIRMED, stored);
		when(control.claim(runId, null, false, false)).thenReturn(new DrawExecutionClaim(confirmed, false));
		when(attempts.stored(confirmed)).thenReturn(result);
		// when / then
		assertThat(service.execute(runId)).isSameAs(result);
		verifyNoInteractions(preparations);
		verify(attempts, never()).execute(any(), any());
	}

	@Test @DisplayName("입력 준비 실패는 같은 실행·시도 ID로 기록하며 선정하지 않는다")
	void recordsPreparationFailureForSameAttempt() {
		// given
		DrawExecutionState running = state(DrawRunStatus.PREPARING);
		DrawExecutionState failed = state(DrawRunStatus.FAILED);
		when(control.claim(runId, null, false, false)).thenReturn(new DrawExecutionClaim(running, true));
		doThrow(new TransientDataAccessResourceException("diagnostic detail")).when(preparations).prepare(runId, attemptId);
		when(control.recordFailure(runId, attemptId, DrawFailureCode.TRANSIENT_STORAGE, NOW)).thenReturn(failed);
		when(attempts.stored(failed)).thenReturn(new DrawExecutionResult(runId, DrawRunStatus.FAILED, null));
		// when
		DrawExecutionResult result = service.execute(runId);
		// then
		assertThat(result.getRunId()).isEqualTo(runId);
		assertThat(result.getConfirmation()).isNull();
		verify(control).recordFailure(runId, attemptId, DrawFailureCode.TRANSIENT_STORAGE, NOW);
		verify(attempts, never()).execute(any(), any());
	}

	@Test @DisplayName("정합성·실행 불가 오류는 자동 재시도 대상으로 분류하지 않는다")
	void doesNotRetryIntegrityOrBlockedExecution() {
		// given / when / then
		assertThat(DrawExecutionService.classify(new BusinessException(INVALID_EVIDENCE))).isEqualTo(DrawFailureCode.INVALID_EVIDENCE);
		assertThat(DrawExecutionService.classify(new BusinessException(NOT_EXECUTABLE))).isEqualTo(DrawFailureCode.NOT_EXECUTABLE);
		assertThat(DrawFailureCode.NOT_EXECUTABLE.isRetryable()).isFalse();
	}
	private DrawExecutionState state(DrawRunStatus status) {
		return new DrawExecutionState(runId, eventId, DrawType.INITIAL, status, status == DrawRunStatus.PREPARING ? null : NOW,
				attemptId, NOW.plusSeconds(300), null, NOW);
	}
}
