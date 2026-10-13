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

/** 미션 제출 Entity. 추가만 하고 수정하지 않는다. {@code createdAt}이 서버가 제출을 받은 시각이다. */
@Getter
@Entity
@Table(name = "mission_submissions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionSubmissionEntity extends BaseEntity {

	@Column(name = "mission_id", nullable = false, updatable = false, length = 16)
	private UUID missionId;

	@Column(name = "reward_policy_id", nullable = false, updatable = false, length = 16)
	private UUID rewardPolicyId;

	@Column(name = "user_id", nullable = false, updatable = false, length = 16)
	private UUID userId;

	@Column(name = "is_completed", nullable = false, updatable = false)
	private boolean completed;

	@Builder
	private MissionSubmissionEntity(UUID missionId, UUID rewardPolicyId, UUID userId, boolean completed) {
		this.missionId = missionId;
		this.rewardPolicyId = rewardPolicyId;
		this.userId = userId;
		this.completed = completed;
	}
}
