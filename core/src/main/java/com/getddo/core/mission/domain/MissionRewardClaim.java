package com.getddo.core.mission.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 미션 완료 보상 청구. 응모권 지급의 근거가 되며 청구 ID로 응모권을 지급한다.
 *
 * <p>사용자·미션 단위로 한 번만 청구할 수 있다({@code UNIQUE (user_id, mission_id)}). 완료 보상은 등급 응모권 1장으로 고정이고
 * 어떤 등급인지는 응모권 지급이 정한다.</p>
 */
@Getter
public final class MissionRewardClaim {

	/** 완료 보상으로 지급하는 응모권 수. */
	public static final int REWARD_TICKET_COUNT = 1;

	/** 저장 전에는 null. */
	private final UUID id;
	private final UUID rewardPolicyId;
	private final UUID missionId;
	private final UUID missionSubmissionId;
	private final UUID userId;
	private final String sourceKey;
	private final int ticketCount;
	/** 저장 전에는 null. */
	private final Instant createdAt;

	public MissionRewardClaim(UUID id, UUID rewardPolicyId, UUID missionId, UUID missionSubmissionId, UUID userId,
			String sourceKey, int ticketCount, Instant createdAt) {
		this.id = id;
		this.rewardPolicyId = Objects.requireNonNull(rewardPolicyId, "rewardPolicyId");
		this.missionId = Objects.requireNonNull(missionId, "missionId");
		this.missionSubmissionId = Objects.requireNonNull(missionSubmissionId, "missionSubmissionId");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
		// DB 제약(chk_mission_claim_ticket)과 같다. 완료 보상은 응모권 1장으로 고정이다.
		MissionRules.definition(ticketCount == REWARD_TICKET_COUNT);
		this.ticketCount = ticketCount;
		this.createdAt = createdAt;
	}

	/**
	 * 저장된 완료 제출로 보상 청구를 만든다.
	 *
	 * @throws com.getddo.core.mission.exception.MissionException 완료하지 못한 제출일 때
	 */
	public static MissionRewardClaim of(MissionSubmission completedSubmission) {
		Objects.requireNonNull(completedSubmission.getId(), "submission.id");
		MissionRules.answer(completedSubmission.isCompleted());
		return new MissionRewardClaim(null, completedSubmission.getRewardPolicyId(), completedSubmission.getMissionId(),
				completedSubmission.getId(), completedSubmission.getUserId(),
				sourceKey(completedSubmission.getMissionId()), REWARD_TICKET_COUNT, null);
	}

	/** 사용자별 미션 보상의 식별 키. 사용자는 별도 컬럼이라 미션 ID만 담는다. */
	public static String sourceKey(UUID missionId) {
		return "mission:" + missionId;
	}
}
