package com.getddo.db.drawing.entity;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;

class DrawCheckEntityTest {
	@Test
	@DisplayName("검증 이력의 필수값은 영속화 전에 null을 거절한다")
	void rejectsMissingRequiredValuesBeforePersistence() {
		// given
		DrawRunEntity run = mock(DrawRunEntity.class);
		UUID actor = UUID.randomUUID();
		Instant now = Instant.parse("2026-10-12T01:00:00Z");
		// when / then
		assertThatNullPointerException().isThrownBy(() -> new DrawCheckEntity(null, actor, false, "{}", now)).withMessage("drawRun");
		assertThatNullPointerException().isThrownBy(() -> new DrawCheckEntity(run, null, false, "{}", now)).withMessage("checkedBy");
		assertThatNullPointerException().isThrownBy(() -> new DrawCheckEntity(run, actor, false, null, now)).withMessage("checksSnapshot");
		assertThatNullPointerException().isThrownBy(() -> new DrawCheckEntity(run, actor, false, "{}", null)).withMessage("checkedAt");
	}
}
