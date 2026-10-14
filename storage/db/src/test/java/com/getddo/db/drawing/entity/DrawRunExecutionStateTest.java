package com.getddo.db.drawing.entity;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.domain.DrawType;
import static org.assertj.core.api.Assertions.*;

class DrawRunExecutionStateTest {
	private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");

	/** 처리 중 응모자 없음으로 종료하면 CHECK 제약에 맞게 소유권을 함께 해제한다. */
	@Test @DisplayName("처리 중 응모자 없음으로 종료하면 CHECK 제약에 맞게 소유권을 함께 해제한다")
	void releasesLeaseForNoEntries() {
		// given
		DrawRunEntity run = new DrawRunEntity(UUID.randomUUID());
		run.claim(UUID.randomUUID(), NOW, NOW.plusSeconds(300), NOW);
		// when
		run.fixInput(DrawRunStatus.NO_ENTRIES, "weighted-without-replacement-v1", "{}", NOW);
		// then
		assertThat(run.getStatus()).isEqualTo(DrawRunStatus.NO_ENTRIES);
		assertThat(run.getAttemptId()).isNull();
		assertThat(run.getLeaseUntil()).isNull();
	}

	/** 고정 입력 실패·재개는 입력과 최초 시작 시각을 보존한다. */
	@Test @DisplayName("고정 입력 실패·재개는 입력과 최초 시작 시각을 보존한다")
	void preservesFixedInputAndFirstStart() {
		// given
		DrawRunEntity run = new DrawRunEntity(UUID.randomUUID());
		run.fixInput(DrawRunStatus.READY, "v1", "{}", NOW);
		run.claim(UUID.randomUUID(), NOW, NOW.plusSeconds(300), NOW);
		// when
		run.fail(NOW.plusSeconds(10));
		run.claim(UUID.randomUUID(), NOW.plusSeconds(10), NOW.plusSeconds(310), NOW);
		run.confirm(NOW.plusSeconds(11));
		// then
		assertThat(run.getSnapshotFixedAt()).isEqualTo(NOW);
		assertThat(run.getStartedAt()).isEqualTo(NOW);
		assertThat(run.getStatus()).isEqualTo(DrawRunStatus.CONFIRMED);
		assertThat(run.getAttemptId()).isNull();
		assertThat(run.getRetryAt()).isNull();
	}

	/** REPLACEMENT 실행에서 NO_ENTRIES 입력 확정이 IllegalArgumentException으로 거절되는지 검증한다. */
	@Test @DisplayName("재추첨 실행의 NO_ENTRIES 입력 확정을 거절한다")
	void replacementCannotTerminateAsInitialNoEntries() {
		// given
		DrawRunEntity run = new DrawRunEntity(UUID.randomUUID(), 1, DrawType.REPLACEMENT);
		// when / then
		assertThatThrownBy(() -> run.fixInput(DrawRunStatus.NO_ENTRIES, "v1", "{}", NOW)).isInstanceOf(IllegalArgumentException.class);
	}
}
