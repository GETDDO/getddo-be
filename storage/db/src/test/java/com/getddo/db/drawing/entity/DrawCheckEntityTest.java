package com.getddo.db.drawing.entity;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;

class DrawCheckEntityTest {
	@Test
	void rejectsMissingRequiredValuesBeforePersistence() {
		DrawRunEntity run = mock(DrawRunEntity.class);
		UUID actor = UUID.randomUUID();
		Instant now = Instant.parse("2026-10-12T01:00:00Z");
		assertThatNullPointerException().isThrownBy(() -> new DrawCheckEntity(null, actor, false, "{}", now)).withMessage("drawRun");
		assertThatNullPointerException().isThrownBy(() -> new DrawCheckEntity(run, null, false, "{}", now)).withMessage("checkedBy");
		assertThatNullPointerException().isThrownBy(() -> new DrawCheckEntity(run, actor, false, null, now)).withMessage("checksSnapshot");
		assertThatNullPointerException().isThrownBy(() -> new DrawCheckEntity(run, actor, false, "{}", null)).withMessage("checkedAt");
	}
}
