package com.getddo.core.drawing.domain;

import java.util.List;
import lombok.Getter;

/** 재추첨 검증에 필요한 최초 입력·과거 확정 배정. 검증 서비스의 같은 읽기 스냅샷에서 조회한다. */
@Getter
public final class DrawVerificationReferences {
	private final DrawRunView originalRun;
	private final List<DrawCandidateView> originalCandidates;
	private final List<DrawHistoricalAward> historicalAwards;

	public DrawVerificationReferences(DrawRunView originalRun, List<DrawCandidateView> originalCandidates,
			List<DrawHistoricalAward> historicalAwards) {
		this.originalRun = originalRun;
		this.originalCandidates = List.copyOf(originalCandidates);
		this.historicalAwards = List.copyOf(historicalAwards);
	}
	public static DrawVerificationReferences empty() {
		return new DrawVerificationReferences(null, List.of(), List.of());
	}
}
