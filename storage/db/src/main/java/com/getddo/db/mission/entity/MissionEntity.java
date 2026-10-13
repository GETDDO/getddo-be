package com.getddo.db.mission.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.getddo.core.mission.domain.MissionStatus;
import com.getddo.core.mission.domain.MissionType;
import com.getddo.db.common.entity.BaseUpdatableEntity;

/** 서비스 공통 미션 Entity. 보상 정책·등록 관리자 참조는 연관관계 없이 UUID로 둔다. */
@Getter
@Entity
@Table(name = "missions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionEntity extends BaseUpdatableEntity {

	@Column(name = "reward_policy_id", nullable = false, updatable = false, length = 16)
	private UUID rewardPolicyId;

	@Column(name = "created_by", nullable = false, updatable = false, length = 16)
	private UUID createdBy;

	@Column(name = "title", nullable = false, updatable = false, length = 200)
	private String title;

	@Column(name = "description", nullable = false, updatable = false, columnDefinition = "text")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "mission_type", nullable = false, updatable = false)
	private MissionType missionType;

	@Column(name = "starts_at", nullable = false, updatable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false, updatable = false)
	private Instant endsAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private MissionStatus status;

	@Column(name = "image_key", updatable = false, length = 500)
	private String imageKey;

	@Builder
	private MissionEntity(UUID rewardPolicyId, UUID createdBy, String title, String description,
			MissionType missionType, Instant startsAt, Instant endsAt, MissionStatus status, String imageKey) {
		this.rewardPolicyId = rewardPolicyId;
		this.createdBy = createdBy;
		this.title = title;
		this.description = description;
		this.missionType = missionType;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.status = status;
		this.imageKey = imageKey;
	}
}
