package com.getddo.core.mission.domain;

import java.util.List;
import java.util.UUID;

import lombok.Getter;

/**
 * 미션 제출 요청.
 *
 * <p>제출 ID는 서버가 만들지 않고 클라이언트가 요청 전에 정한 UUID({@code Idempotency-Key})다. 같은 제출의 재시도는 같은 값을
 * 쓴다. 값의 유효성은 {@code MissionSubmissionService}가 검증한다.</p>
 */
@Getter
public final class MissionSubmissionCommand {

	private final UUID userId;
	private final UUID missionId;
	private final UUID submissionId;
	private final List<MissionAnswerInput> answers;

	public MissionSubmissionCommand(UUID userId, UUID missionId, UUID submissionId, List<MissionAnswerInput> answers) {
		this.userId = userId;
		this.missionId = missionId;
		this.submissionId = submissionId;
		this.answers = answers == null ? null : List.copyOf(answers);
	}
}
