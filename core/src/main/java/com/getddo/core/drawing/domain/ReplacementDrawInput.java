package com.getddo.core.drawing.domain;

import java.util.List;
import java.util.UUID;

/** 재추첨 전용 계산 결과. 공통 스냅샷 버전·저장 계약이 확정되면 준비 구현에서 변환한다. */
public record ReplacementDrawInput(UUID originalRunId, DrawSnapshot.Rules originalRules,
		List<DrawSnapshot.Candidate> candidates, List<Slot> vacantSlots, List<DrawResult> retainedResults) {
	public ReplacementDrawInput {
		candidates = List.copyOf(candidates);
		vacantSlots = List.copyOf(vacantSlots);
		retainedResults = List.copyOf(retainedResults);
	}

	/** 최초 경품 조건의 등수와 실제 취소 자리 번호. */
	public record Slot(UUID prizeId, int rank, int slotNumber) { }
}
