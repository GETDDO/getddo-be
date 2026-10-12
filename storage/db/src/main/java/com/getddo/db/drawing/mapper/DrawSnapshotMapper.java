package com.getddo.db.drawing.mapper;

import java.util.List;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.db.drawing.entity.DrawCandidateEntity;
import com.getddo.db.drawing.entity.DrawRunEntity;

/**
 * DB JSON과 core 모델의 변환을 영속성 모듈에서 수행한다.
 * JSON 직렬화·복원과 실행 엔티티의 입력 확정은 명시적으로 처리하며 현재 응모 원본을 조회하지 않는다.
 */
@Component
public class DrawSnapshotMapper {
	private final JsonMapper json = JsonMapper.builder().build();

	/** 확정 후보의 수량·가중치와 버전별 응모·자격 근거를 신규 후보 행으로 변환한다. */
	public DrawCandidateEntity toEntity(DrawRunEntity run, DrawSnapshot.Candidate candidate) {
		return new DrawCandidateEntity(run, candidate.participantId(), candidate.ticketCount(), candidate.weight(),
				json.writeValueAsString(candidate.entryEvidence()), json.writeValueAsString(candidate.eligibilityEvidence()));
	}

	/** 저장할 조건을 JSON으로 직렬화하고 실행 엔티티의 일회성 입력 확정 규칙을 적용한다. */
	public void fixInput(DrawRunEntity run, DrawSnapshot snapshot) {
		run.fixInput(snapshot.status(), snapshot.algorithmVersion(), json.writeValueAsString(snapshot.rules()), snapshot.fixedAt());
	}

	/** 실행 행과 해당 실행에 연결된 후보만으로 기존 스냅샷을 복원한다. */
	public DrawSnapshot toDomain(DrawRunEntity run, List<DrawCandidateEntity> candidates) {
		DrawSnapshot.Rules rules = run.getRulesSnapshot() == null ? null
				: json.readValue(run.getRulesSnapshot(), DrawSnapshot.Rules.class);
		return new DrawSnapshot(run.getId(), run.getEventId(), run.getStatus(), run.getSnapshotFixedAt(),
				run.getAlgorithmVersion(), rules, candidates.stream().map(this::toDomain).toList());
	}

	/** 현재 사용자 정보 대신 저장된 자격 근거에서 사용자 식별자와 과거 excluded 값을 복원한다. */
	private DrawSnapshot.Candidate toDomain(DrawCandidateEntity candidate) {
		DrawSnapshot.EligibilityEvidence eligibility = json.readValue(candidate.getEligibilitySnapshot(), DrawSnapshot.EligibilityEvidence.class);
		return new DrawSnapshot.Candidate(candidate.getId(), candidate.getParticipantId(), eligibility.userId(),
				candidate.getTicketCount(), candidate.getWeight(),
				json.readValue(candidate.getEntrySnapshot(), DrawSnapshot.EntryEvidence.class), eligibility);
	}
}
