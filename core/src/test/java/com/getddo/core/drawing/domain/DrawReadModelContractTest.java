package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DrawReadModelContractTest {

	@Test
	@DisplayName("상세 조회의 필수 실행과 결과 목록은 생성할 때 누락을 거절한다")
	void rejectsMissingDetailFields() {
		// given
		DrawInspectionView.Run run = run();
		// when / then
		assertThatNullPointerException().isThrownBy(() -> new DrawInspectionView.Detail(null, List.of()))
				.withMessage("run");
		assertThatNullPointerException().isThrownBy(() -> new DrawInspectionView.Detail(run, null))
				.withMessage("results");
	}

	@Test
	@DisplayName("검증 참고 자료의 후보와 과거 배정 목록은 누락을 거절한다")
	void rejectsMissingReferenceLists() {
		// given / when / then
		assertThatNullPointerException().isThrownBy(() -> new DrawVerification.References(null, null, List.of()))
				.withMessage("originalCandidates");
		assertThatNullPointerException().isThrownBy(() -> new DrawVerification.References(null, List.of(), null))
				.withMessage("historicalAwards");
	}

	@Test
	@DisplayName("운영 요약 없는 상세 조회와 최초 실행 없는 빈 참고 자료는 계속 허용한다")
	void retainsOptionalFieldsAndEmptyReferences() {
		// given
		DrawInspectionView.Run run = run();
		// when
		DrawInspectionView.Detail detail = new DrawInspectionView.Detail(run, List.of());
		DrawVerification.References references = DrawVerification.References.empty();
		// then
		assertThat(detail.getRun()).isSameAs(run);
		assertThat(detail.getResults()).isEmpty();
		assertThat(detail.getExecution()).isNull();
		assertThat(references.getOriginalRun()).isNull();
		assertThat(references.getOriginalCandidates()).isEmpty();
		assertThat(references.getHistoricalAwards()).isEmpty();
	}

	private DrawInspectionView.Run run() {
		Instant now = Instant.parse("2026-10-14T00:00:00Z");
		DrawSnapshot.Rules rules = new DrawSnapshot.Rules(1, false, "TICKET", "ALL", 1, 3, 5, List.of());
		return new DrawInspectionView.Run(UUID.randomUUID(), UUID.randomUUID(), 0, DrawType.INITIAL,
				DrawRunStatus.CONFIRMED, null, "v1", rules, now, now, now, now);
	}
}
