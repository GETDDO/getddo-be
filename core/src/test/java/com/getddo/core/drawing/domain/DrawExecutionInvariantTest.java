package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.getddo.core.drawing.exception.DrawingErrorCode;
import static org.assertj.core.api.Assertions.*;

class DrawExecutionInvariantTest {
	private final UUID runId = UUID.randomUUID(), eventId = UUID.randomUUID(), token = UUID.randomUUID();
	private final Instant now = Instant.parse("2026-10-13T00:00:00Z");

	@Test @DisplayName("획득한 시도와 거절 코드의 모순 및 상태 누락을 생성 시 거절한다")
	void rejectsContradictoryClaim() {
		// given
		DrawExecutionState state = state(DrawRunStatus.READY, token, null, now);
		// when / then
		assertThatNullPointerException().isThrownBy(() -> new DrawExecutionClaim(null, false));
		assertThatIllegalArgumentException().isThrownBy(() -> new DrawExecutionClaim(state, true, DrawingErrorCode.INVALID_RUN));
		assertThat(new DrawExecutionClaim(state, false, DrawingErrorCode.INVALID_RUN).isClaimed()).isFalse();
	}

	@Test @DisplayName("DB CHECK와 다른 소유권·예약 조합을 생성할 수 없다")
	void rejectsImpossibleExecutionStates() {
		// given / when / then
		assertThatIllegalArgumentException().isThrownBy(() -> state(DrawRunStatus.FAILED, token, null, now));
		assertThatIllegalArgumentException().isThrownBy(() -> state(DrawRunStatus.READY, token, null, null));
		assertThatIllegalArgumentException().isThrownBy(() -> state(DrawRunStatus.READY, null, now, now));
		assertThatIllegalArgumentException().isThrownBy(() -> state(DrawRunStatus.FAILED, null, now, null));
		assertThat(state(DrawRunStatus.FAILED, null, now, now).getRetryAt()).isEqualTo(now);
		assertThat(state(DrawRunStatus.RUNNING, null, null, null).getAttemptId()).isNull();
	}

	@Test @DisplayName("스냅샷 버전별 재추첨 연결 정보와 실행 종류를 검증한다")
	void validatesVersionedSnapshotShape() {
		// given
		List<DrawSnapshot.Slot> targets = List.of(new DrawSnapshot.Slot(UUID.randomUUID(), 1, 1));
		// when / then
		assertThatIllegalArgumentException().isThrownBy(() -> rules(3, null, List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> rules(1, runId, targets));
		assertThatIllegalArgumentException().isThrownBy(() -> rules(2, null, targets));
		assertThatIllegalArgumentException().isThrownBy(() -> rules(2, runId, List.of()));
		assertThat(rules(1, null, null).targetSlots()).isEmpty();
		assertThat(rules(2, runId, targets).retainedAwards()).isEmpty();
		assertThatNullPointerException().isThrownBy(() -> new DrawSnapshot(runId, eventId, DrawRunStatus.READY,
				now, "v1", rules(1, null, List.of()), List.of(), null));
	}

	private DrawExecutionState state(DrawRunStatus status, UUID attempt, Instant retry, Instant cycle) {
		return new DrawExecutionState(runId, eventId, DrawType.INITIAL, status, now, attempt,
				attempt == null ? null : now.plusSeconds(300), retry, cycle);
	}
	private DrawSnapshot.Rules rules(int version, UUID original, List<DrawSnapshot.Slot> slots) {
		return new DrawSnapshot.Rules(version, false, "NO_TICKET", "ALL", 1, 3, 5, List.of(), original, slots, List.of());
	}
}
