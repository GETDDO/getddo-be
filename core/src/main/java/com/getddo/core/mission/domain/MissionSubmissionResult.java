package com.getddo.core.mission.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 미션 제출 결과.
 *
 * <p>정답을 맞히지 못한 유효한 제출도 정상 결과이며 {@code completed=false}, {@code reward=null}이다. 같은 제출 ID의 재요청이나
 * 이미 완료한 미션의 재제출에는 이전에 확정된 결과를 {@code created=false}로 돌려준다.</p>
 */
@Getter
public final class MissionSubmissionResult {

	private final UUID submissionId;
	private final UUID missionId;
	/** 서버가 제출을 받은 시각. */
	private final Instant receivedAt;
	private final boolean completed;
	/** 완료하지 못했으면 null. */
	private final MissionRewardReceipt reward;
	/** 이번 호출에서 새로 접수했으면 true, 이전에 확정된 결과를 돌려줬으면 false. */
	private final boolean created;

	public MissionSubmissionResult(UUID submissionId, UUID missionId, Instant receivedAt, boolean completed,
			MissionRewardReceipt reward, boolean created) {
		this.submissionId = Objects.requireNonNull(submissionId, "submissionId");
		this.missionId = Objects.requireNonNull(missionId, "missionId");
		this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
		this.completed = completed;
		this.reward = reward;
		this.created = created;
	}
}
