package com.getddo.db.ticket;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 호출자(미션)가 JPA로 청구를 저장하는 상황을 흉내 내는 테스트 전용 Entity.
 *
 * <p>미션 도메인의 실제 Entity는 아직 없고 응모권 작업에서 만들지 않는다. {@code persist}만 하고 flush하지 않은
 * 청구도 {@code grant}가 찾을 수 있는지 검증하는 데만 쓴다.</p>
 */
@Entity
@Table(name = "mission_reward_claims")
public class PendingMissionClaim {

	@Id
	@Column(name = "id", length = 16)
	private UUID id;

	@Column(name = "user_id", length = 16)
	private UUID userId;

	@Column(name = "reward_policy_id", length = 16)
	private UUID rewardPolicyId;

	@Column(name = "mission_id", length = 16)
	private UUID missionId;

	@Column(name = "mission_submission_id", length = 16)
	private UUID missionSubmissionId;

	@Column(name = "source_key", length = 160)
	private String sourceKey;

	@Column(name = "ticket_count")
	private int ticketCount;

	@Column(name = "created_at")
	private Instant createdAt;

	protected PendingMissionClaim() {
	}

	PendingMissionClaim(UUID userId, TicketGrantSeeds.MissionParents parents, int ticketCount) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.rewardPolicyId = parents.policyId();
		this.missionId = parents.missionId();
		this.missionSubmissionId = parents.submissionId();
		this.sourceKey = "pending-" + id;
		this.ticketCount = ticketCount;
		this.createdAt = Instant.parse("2026-09-01T00:00:00Z");
	}

	UUID getId() {
		return id;
	}
}
