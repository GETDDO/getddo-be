package com.getddo.db.drawing.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.getddo.core.drawing.domain.DrawRunStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DrawRunEntityTest {
	private static final Instant FIXED_AT = Instant.parse("2026-10-10T06:00:00Z");

	/** 필수 입력 누락은 엔티티를 변경하지 않고 이후 정상 입력 확정도 허용하는지 검증한다. */
	@ParameterizedTest(name = "{0}: {1} 누락 시 상태를 변경하지 않는다")
	@MethodSource("incompleteInputs")
	void rejectsIncompleteInputBeforeChangingState(DrawRunStatus targetStatus, String missingField) {
		DrawRunEntity run = new DrawRunEntity(UUID.randomUUID());
		String algorithm = missingField.equals("algorithmVersion") ? null : "v1";
		String rules = missingField.equals("rulesSnapshot") ? null : "{}";
		Instant fixedAt = missingField.equals("fixedAt") ? null : FIXED_AT;

		assertThatThrownBy(() -> run.fixInput(targetStatus, algorithm, rules, fixedAt))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(run.getStatus()).isEqualTo(DrawRunStatus.PREPARING);
		assertThat(run.getAlgorithmVersion()).isNull();
		assertThat(run.getRulesSnapshot()).isNull();
		assertThat(run.getSnapshotFixedAt()).isNull();

		// 누락된 입력을 거절한 뒤에도 올바른 입력으로 정상 확정할 수 있어야 한다.
		run.fixInput(targetStatus, "v1", "{}", FIXED_AT);
		assertThat(run.getStatus()).isEqualTo(targetStatus);
		assertThat(run.getAlgorithmVersion()).isEqualTo("v1");
		assertThat(run.getRulesSnapshot()).isEqualTo("{}");
		assertThat(run.getSnapshotFixedAt()).isEqualTo(FIXED_AT);
	}

	/** 신규 NO_CANDIDATES 확정을 거절하고 PREPARING 입력을 보존하는지 검증한다. */
	@Test
	@DisplayName("신규 전원 제외 종료를 거절하고 확정 전 입력을 보존한다")
	void rejectsNewAllExcludedTerminationWithoutChangingInput() {
		// given
		DrawRunEntity run = new DrawRunEntity(UUID.randomUUID());

		// when
		assertThatThrownBy(() -> run.fixInput(DrawRunStatus.NO_CANDIDATES, "v1", "{}", FIXED_AT))
				.isInstanceOf(IllegalArgumentException.class);

		// then
		assertThat(run.getStatus()).isEqualTo(DrawRunStatus.PREPARING);
		assertThat(run.getAlgorithmVersion()).isNull();
		assertThat(run.getRulesSnapshot()).isNull();
		assertThat(run.getSnapshotFixedAt()).isNull();
	}

	/** 신규 확정 상태별 알고리즘·규칙·시각 누락 사례를 제공한다. */
	private static Stream<Arguments> incompleteInputs() {
		return Stream.of(DrawRunStatus.READY, DrawRunStatus.NO_ENTRIES)
				.flatMap(status -> List.of("algorithmVersion", "rulesSnapshot", "fixedAt").stream()
						.map(field -> Arguments.of(status, field)));
	}
}
