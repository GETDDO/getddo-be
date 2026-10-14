package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DrawExecutionStateTest {
	private static final UUID RUN_ID = UUID.randomUUID();
	private static final UUID EVENT_ID = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");

	/** 실행 식별자·이벤트·종류·상태가 없으면 상태 모델을 만들지 않는다. */
	@Test
	@DisplayName("실행 식별자·이벤트·종류·상태가 없으면 상태 모델을 만들지 않는다")
	void rejectsMissingRequiredValues() {
		// when / then
		assertThatNullPointerException().isThrownBy(() -> new DrawExecutionState(null, EVENT_ID,
				DrawType.INITIAL, DrawRunStatus.PREPARING, null, null, null, null, null)).withMessage("runId");
		assertThatNullPointerException().isThrownBy(() -> new DrawExecutionState(RUN_ID, null,
				DrawType.INITIAL, DrawRunStatus.PREPARING, null, null, null, null, null)).withMessage("eventId");
		assertThatNullPointerException().isThrownBy(() -> new DrawExecutionState(RUN_ID, EVENT_ID,
				null, DrawRunStatus.PREPARING, null, null, null, null, null)).withMessage("drawType");
		assertThatNullPointerException().isThrownBy(() -> new DrawExecutionState(RUN_ID, EVENT_ID,
				DrawType.INITIAL, null, null, null, null, null, null)).withMessage("status");
	}

	/** 시도 토큰과 임대 시각 중 하나만 있는 상태를 거절한다. */
	@ParameterizedTest
	@CsvSource({"true, false", "false, true"})
	@DisplayName("시도 토큰과 임대 시각 중 하나만 있는 상태를 거절한다")
	void rejectsIncompleteOwnership(boolean hasToken, boolean hasLease) {
		// given
		UUID token = hasToken ? UUID.randomUUID() : null;
		Instant lease = hasLease ? NOW.plusSeconds(300) : null;
		// when / then
		assertThatIllegalArgumentException().isThrownBy(() -> new DrawExecutionState(RUN_ID, EVENT_ID,
				DrawType.INITIAL, DrawRunStatus.RUNNING, NOW, token, lease, null, NOW));
	}

	/** 소유권 없는 과거 RUNNING 상태는 복구 대상으로 복원할 수 있다. */
	@Test
	@DisplayName("소유권 없는 과거 RUNNING 상태는 복구 대상으로 복원할 수 있다")
	void preservesLegacyRunningWithoutOwnership() {
		// when
		DrawExecutionState state = new DrawExecutionState(RUN_ID, EVENT_ID, DrawType.INITIAL,
				DrawRunStatus.RUNNING, NOW, null, null, null, null);
		// then
		assertThat(state.getStatus()).isEqualTo(DrawRunStatus.RUNNING);
		assertThat(state.getAttemptId()).isNull();
	}
}
