package com.getddo.db.drawing.mapper;

import java.util.List;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.db.drawing.entity.DrawCandidateEntity;
import com.getddo.db.drawing.entity.DrawRunEntity;

/** DB JSON과 core 모델의 변환을 영속성 모듈에서 수행한다. */
@Component
public class DrawSnapshotMapper {
	private final JsonMapper json = JsonMapper.builder().build();

	public DrawCandidateEntity toEntity(DrawRunEntity run, DrawSnapshot.Candidate candidate) {
		return new DrawCandidateEntity(run, candidate.participantId(), candidate.ticketCount(), candidate.weight(),
				json.writeValueAsString(candidate.entryEvidence()), json.writeValueAsString(candidate.eligibilityEvidence()));
	}

	public void fixInput(DrawRunEntity run, DrawSnapshot snapshot) {
		run.fixInput(snapshot.status(), snapshot.algorithmVersion(), json.writeValueAsString(snapshot.rules()), snapshot.fixedAt());
	}

	public DrawSnapshot toDomain(DrawRunEntity run, List<DrawCandidateEntity> candidates) {
		DrawSnapshot.Rules rules = run.getRulesSnapshot() == null ? null
				: json.readValue(run.getRulesSnapshot(), DrawSnapshot.Rules.class);
		return new DrawSnapshot(run.getId(), run.getEventId(), run.getStatus(), run.getSnapshotFixedAt(),
				run.getAlgorithmVersion(), rules, candidates.stream().map(this::toDomain).toList());
	}

	private DrawSnapshot.Candidate toDomain(DrawCandidateEntity candidate) {
		DrawSnapshot.EligibilityEvidence eligibility = json.readValue(candidate.getEligibilitySnapshot(), DrawSnapshot.EligibilityEvidence.class);
		return new DrawSnapshot.Candidate(candidate.getId(), candidate.getParticipantId(), eligibility.userId(),
				candidate.getTicketCount(), candidate.getWeight(),
				json.readValue(candidate.getEntrySnapshot(), DrawSnapshot.EntryEvidence.class), eligibility);
	}
}
