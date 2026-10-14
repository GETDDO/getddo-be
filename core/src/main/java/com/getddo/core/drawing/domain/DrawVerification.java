package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Getter;

/** 관리자가 검사한 추첨의 통과 여부·검사 항목·처리자·시각을 보존한다. */
@Getter
public final class DrawVerification {
	private final UUID id;
	private final UUID drawRunId;
	private final boolean passed;
	private final List<DrawVerification.Item> checks;
	private final Instant verifiedAt;
	private final UUID verifiedBy;

	public DrawVerification(UUID id, UUID drawRunId, boolean passed, List<DrawVerification.Item> checks,
			Instant verifiedAt, UUID verifiedBy) {
		this.id = id;
		this.drawRunId = drawRunId;
		this.passed = passed;
		this.checks = List.copyOf(checks);
		this.verifiedAt = verifiedAt;
		this.verifiedBy = verifiedBy;
	}

	/** 수량·가중치·자리 등의 검사 한 항목에 대한 코드·통과 여부·안내다. */
	@Getter
	public static final class Item {
		private final String code;
		private final boolean passed;
		private final String message;

		public Item(String code, boolean passed, String message) {
			this.code = code;
			this.passed = passed;
			this.message = message;
		}
	}

	/** 재추첨 검사에 사용할 최초 입력과 과거 자리 배정이다. 검증 데이터와 같은 조회 트랜잭션에서 읽는다. */
	@Getter
	public static final class References {
		private final DrawInspectionView.Run originalRun;
		private final List<DrawInspectionView.Candidate> originalCandidates;
		private final List<DrawHistoricalAward> historicalAwards;

		public References(DrawInspectionView.Run originalRun, List<DrawInspectionView.Candidate> originalCandidates,
				List<DrawHistoricalAward> historicalAwards) {
			this.originalRun = originalRun;
			this.originalCandidates = List.copyOf(originalCandidates);
			this.historicalAwards = List.copyOf(historicalAwards);
		}
		public static DrawVerification.References empty() {
			return new DrawVerification.References(null, List.of(), List.of());
		}
	}
}
