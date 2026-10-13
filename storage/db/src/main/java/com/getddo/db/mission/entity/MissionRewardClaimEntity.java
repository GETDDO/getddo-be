package com.getddo.db.mission.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.getddo.db.common.entity.BaseEntity;

/** 미션 보상 청구 Entity. 수정하지 않는다. {@code (user_id, mission_id)}가 유일해 사용자별 미션 보상을 한 번만 받게 한다. */
@Getter
@Entity
@Table(name = "mission_reward_claims")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionRewardClaimEntity extends BaseEntity {

	@Column(name = "reward_policy_id", nullable = false, updatable = false, length = 16)
	private UUID rewardPolicyId;

	@Column(name = "mission_id", nullable = false, updatable = false, length = 16)
	private UUID missionId;

	@Column(name = "mission_submission_id", nullable = false, updatable = false, length = 16)
	private UUID missionSubmissionId;

	@Column(name = "user_id", nullable = false, updatable = false, length = 16)
	private UUID userId;

	@Column(name = "source_key", nullable = false, updatable = false, length = 160)
	private String sourceKey;

	@Column(name = "ticket_count", nullable = false, updatable = false)
	private int ticketCount;

	@Builder
	private MissionRewardClaimEntity(UUID rewardPolicyId, UUID missionId, UUID missionSubmissionId, UUID userId,
			String sourceKey, int ticketCount) {
		this.rewardPolicyId = rewardPolicyId;
		this.missionId = missionId;
		this.missionSubmissionId = missionSubmissionId;
		this.userId = userId;
		this.sourceKey = sourceKey;
		this.ticketCount = ticketCount;
	}
}
