package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** READY 이후 재요청은 이 저장된 값만 반환한다. JSON의 버전은 알고리즘 버전과 별개다. */
public record DrawSnapshot(UUID runId, UUID eventId, DrawRunStatus status, Instant fixedAt,
		String algorithmVersion, Rules rules, List<Candidate> candidates) {
	public DrawSnapshot { candidates = List.copyOf(candidates); }

	public record Rules(int version, boolean weightingEnabled, String eventType, String membershipRule,
			int bronzeWeight, int silverWeight, int goldWeight, List<DrawSnapshotSource.Prize> prizes) {
		public Rules { prizes = List.copyOf(prizes); }
	}

	public record Candidate(UUID id, UUID participantId, UUID userId, long ticketCount, long weight,
			EntryEvidence entryEvidence, EligibilityEvidence eligibilityEvidence) { }

	public record EntryEvidence(int version, long bronzeCount, long silverCount, long goldCount,
			List<DrawSnapshotSource.Entry> entries) {
		public EntryEvidence { entries = List.copyOf(entries); }
	}

	public record EligibilityEvidence(int version, UUID userId, String membership, String role,
			boolean excluded, Instant checkedAt) { }
}
