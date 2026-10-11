package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** READY 이후 재요청은 이 저장된 값만 반환한다. JSON의 버전은 알고리즘 버전과 별개다. */
public record DrawSnapshot(UUID runId, UUID eventId, DrawRunStatus status, Instant fixedAt,
		String algorithmVersion, Rules rules, List<Candidate> candidates) {
	/** 후보 목록을 복사해 외부 컬렉션 변경이 확정 입력에 영향을 주지 않게 한다. */
	public DrawSnapshot { candidates = List.copyOf(candidates); }

	/** 실행 당시 이벤트 유형·멤버십·등급별 가중치와 경품 조건을 보존한다. */
	public record Rules(int version, boolean weightingEnabled, String eventType, String membershipRule,
			int bronzeWeight, int silverWeight, int goldWeight, List<DrawSnapshotSource.Prize> prizes) {
		/** 경품 조건 목록을 변경 불가능한 값으로 보존한다. */
		public Rules { prizes = List.copyOf(prizes); }
	}

	/** 실제 사용 장수와 선정 가중치를 구분하고 응모·자격 근거를 함께 보존한 후보다. */
	public record Candidate(UUID id, UUID participantId, UUID userId, long ticketCount, long weight,
			EntryEvidence entryEvidence, EligibilityEvidence eligibilityEvidence) { }

	/** 확정 가중치의 근거인 등급별 실제 차감량과 정상 접수된 응모를 보존한다. */
	public record EntryEvidence(int version, long bronzeCount, long silverCount, long goldCount,
			List<DrawSnapshotSource.Entry> entries) {
		/** 응모 근거 목록을 변경 불가능한 값으로 보존한다. */
		public EntryEvidence { entries = List.copyOf(entries); }
	}

	/** excluded는 과거 JSON 호환용이며 새 후보에는 false를 저장한다. */
	public record EligibilityEvidence(int version, UUID userId, String membership, String role,
			boolean excluded, Instant checkedAt) { }
}
