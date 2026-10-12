package com.getddo.db.drawing.entity;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.drawing.domain.DrawPrize;
import com.getddo.core.drawing.domain.DrawSelection;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** DB에 도달하기 전에 필수 참조 누락을 거절하는 결과 엔티티의 생성 경계를 검증한다. */
class DrawResultEntityTest {

	/** 실행이 없는 결과를 구성한 뒤 flush까지 오류를 미루지 않도록 검증한다. */
	@Test
	@DisplayName("실행이 없으면 결과 엔티티 생성 단계에서 거절한다")
	void rejectsMissingRun() {
		// given
		DrawSelection selection = DrawSelection.unfilled(new DrawPrize(UUID.randomUUID(), 1, 1), 1);
		// when / then
		assertThatNullPointerException().isThrownBy(() -> new DrawResultEntity(null, selection)).withMessage("run");
	}

	/** 선정 값이 없으면 필드 매핑 이전에 누락된 참조 이름을 알려주는지 검증한다. */
	@Test
	@DisplayName("선정 값이 없으면 결과 엔티티 생성 단계에서 거절한다")
	void rejectsMissingSelection() {
		// given
		DrawRunEntity run = new DrawRunEntity(UUID.randomUUID());
		// when / then
		assertThatNullPointerException().isThrownBy(() -> new DrawResultEntity(run, null)).withMessage("selection");
	}
}
