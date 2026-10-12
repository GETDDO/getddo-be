package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

/** READY 이후 재요청은 이 저장된 값만 반환한다. JSON의 버전은 알고리즘 버전과 별개다. */
public record DrawSnapshot(UUID runId, UUID eventId, DrawRunStatus status, Instant fixedAt,
		String algorithmVersion, Rules rules, List<Candidate> candidates, DrawType drawType) {
	/** 기존 최초 실행 생성 호출과 호환된다. */
	public DrawSnapshot(UUID runId, UUID eventId, DrawRunStatus status, Instant fixedAt,
			String algorithmVersion, Rules rules, List<Candidate> candidates) {
		this(runId, eventId, status, fixedAt, algorithmVersion, rules, candidates, DrawType.INITIAL);
	}
	/** 후보 목록을 복사해 외부 컬렉션 변경이 확정 입력에 영향을 주지 않게 한다. */
	public DrawSnapshot {
		Objects.requireNonNull(drawType, "drawType");
		candidates = List.copyOf(candidates);
	}

	/** 실행 당시 이벤트 유형·멤버십·등급별 가중치와 경품 조건을 보존한다. */
	public record Rules(int version, boolean weightingEnabled, String eventType, String membershipRule,
			int bronzeWeight, int silverWeight, int goldWeight, List<DrawSnapshotSource.Prize> prizes,
			UUID originalRunId, List<Slot> targetSlots, List<RetainedAward> retainedAwards) {
		public Rules(int version, boolean weightingEnabled, String eventType, String membershipRule,
				int bronzeWeight, int silverWeight, int goldWeight, List<DrawSnapshotSource.Prize> prizes) {
			this(version, weightingEnabled, eventType, membershipRule, bronzeWeight, silverWeight, goldWeight,
					prizes, null, List.of(), List.of());
		}
		/** 경품 조건 목록을 변경 불가능한 값으로 보존한다. */
		public Rules {
			prizes = List.copyOf(prizes);
			targetSlots = targetSlots == null ? List.of() : List.copyOf(targetSlots);
			retainedAwards = retainedAwards == null ? List.of() : List.copyOf(retainedAwards);
			if (version != 1 && version != 2) throw new IllegalArgumentException("지원하지 않는 추첨 규칙 버전입니다.");
			if (version == 1 && (originalRunId != null || !targetSlots.isEmpty() || !retainedAwards.isEmpty())) {
				throw new IllegalArgumentException("최초 규칙에는 재추첨 연결 정보를 넣을 수 없습니다.");
			}
			if (version == 2 && (originalRunId == null || targetSlots.isEmpty())) {
				throw new IllegalArgumentException("재추첨 규칙에는 최초 실행과 대상 자리가 필요합니다.");
			}
		}
	}

	/** 버전 2 재추첨의 실제 경품 자리. 전체 경품 조건과 별도로 보존한다. */
	public record Slot(UUID prizeId, int rank, int slotNumber) { }
	/** 입력 고정 시 유지할 배정의 불변 식별자와 사용자·자리. */
	public record RetainedAward(UUID resultId, UUID userId, UUID prizeId, int slotNumber) { }

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
