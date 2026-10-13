package com.getddo.core.drawing.domain;

import java.util.List;
import java.util.UUID;

/** 최초 입력과 GD-87 Rules 버전 2의 실제 빈 자리를 보존하는 재추첨 계산 결과. */
public record ReplacementDrawInput(UUID originalRunId, DrawSnapshot.Rules originalRules,
		List<DrawSnapshot.Candidate> candidates, List<DrawSnapshot.Slot> vacantSlots, List<DrawResult> retainedResults) {
	public ReplacementDrawInput {
		candidates = List.copyOf(candidates);
		vacantSlots = List.copyOf(vacantSlots);
		retainedResults = List.copyOf(retainedResults);
	}
}
