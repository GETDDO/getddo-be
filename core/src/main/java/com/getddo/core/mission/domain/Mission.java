package com.getddo.core.mission.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 서비스 공통 미션. 특정 이벤트에 속하지 않으며 운영 시작·종료 시각 안에서만 제출할 수 있다.
 *
 * <p>운영 중인 미션의 문항·조건·보상은 바꾸지 않으므로 값을 바꾸는 메서드를 두지 않는다. 변경은 새 미션을 등록한다.</p>
 */
@Getter
public final class Mission {

	public static final int MAX_TITLE_LENGTH = 200;
	public static final int MAX_IMAGE_KEY_LENGTH = 500;

	/** 저장 전에는 null. */
	private final UUID id;
	private final UUID rewardPolicyId;
	private final UUID createdBy;
	private final String title;
	private final String description;
	private final MissionType missionType;
	private final Instant startsAt;
	private final Instant endsAt;
	private final MissionStatus status;
	/** 설명 이미지 한 개의 저장소 경로. 없으면 null. */
	private final String imageKey;
	/** 저장 전에는 null. */
	private final Instant createdAt;
	/** 저장 전에는 null. */
	private final Instant updatedAt;

	public Mission(UUID id, UUID rewardPolicyId, UUID createdBy, String title, String description,
			MissionType missionType, Instant startsAt, Instant endsAt, MissionStatus status, String imageKey,
			Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.rewardPolicyId = Objects.requireNonNull(rewardPolicyId, "rewardPolicyId");
		this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
		this.title = Objects.requireNonNull(title, "title");
		this.description = Objects.requireNonNull(description, "description");
		this.missionType = Objects.requireNonNull(missionType, "missionType");
		this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
		this.endsAt = Objects.requireNonNull(endsAt, "endsAt");
		this.status = Objects.requireNonNull(status, "status");
		this.imageKey = imageKey;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	/**
	 * 새 미션을 운영 상태로 등록한다.
	 *
	 * @throws com.getddo.core.mission.exception.MissionException 제목이 비었거나 200자를 넘을 때, 설명이 비었을 때,
	 *         종료 시각이 시작 시각 뒤가 아닐 때, 이미지 경로가 500자를 넘을 때
	 */
	public static Mission register(UUID rewardPolicyId, UUID createdBy, String title, String description,
			MissionType missionType, Instant startsAt, Instant endsAt, String imageKey) {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(description, "description");
		Objects.requireNonNull(startsAt, "startsAt");
		Objects.requireNonNull(endsAt, "endsAt");
		MissionRules.definition(MissionRules.hasText(title) && title.length() <= MAX_TITLE_LENGTH);
		MissionRules.definition(MissionRules.hasText(description));
		MissionRules.definition(endsAt.isAfter(startsAt));
		MissionRules.definition(imageKey == null || imageKey.length() <= MAX_IMAGE_KEY_LENGTH);
		return new Mission(null, rewardPolicyId, createdBy, title, description, missionType, startsAt, endsAt,
				MissionStatus.ACTIVE, imageKey, null, null);
	}

	/**
	 * 서버가 제출을 받은 시각에 제출을 인정하는지 확인한다. 시작 시각 이상, 종료 시각 미만이고 운영 중일 때만 참이다.
	 * 정확히 종료 시각에 도착한 제출은 인정하지 않는다.
	 */
	public boolean acceptsSubmissionAt(Instant receivedAt) {
		return status == MissionStatus.ACTIVE && !receivedAt.isBefore(startsAt) && receivedAt.isBefore(endsAt);
	}
}
