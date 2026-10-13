package com.getddo.api.mission.dto.response;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;

import com.getddo.core.mission.domain.MissionSubmissionResult;

/**
 * 미션 제출 결과의 공개 필드다.
 *
 * <p>정답을 맞히지 못한 유효한 제출도 정상 결과이며 {@code isCompleted=false}, {@code reward=null}이다. 정답 자체는 응답에 담지
 * 않는다. {@code createdAt}은 서버가 제출을 받은 시각으로 {@code receivedAt}과 같다.</p>
 *
 * @param submissionId 제출 ID. 요청의 {@code Idempotency-Key}와 같다. 이미 완료한 미션의 재제출이면 먼저 완료한 제출의 ID다
 * @param isCompleted 이 제출로(또는 이미) 미션을 완료했는지 여부
 * @param reward 완료로 지급한 응모권. 완료하지 못했으면 null
 */
@Builder
public record MissionSubmissionResponse(
		UUID submissionId,
		UUID missionId,
		Instant receivedAt,
		boolean isCompleted,
		RewardReceiptResponse reward,
		Instant createdAt) {

	/** 서비스가 돌려준 결과를 응답으로 옮긴다. */
	public static MissionSubmissionResponse from(MissionSubmissionResult result) {
		return MissionSubmissionResponse.builder()
				.submissionId(result.getSubmissionId())
				.missionId(result.getMissionId())
				.receivedAt(result.getReceivedAt())
				.isCompleted(result.isCompleted())
				.reward(result.getReward() == null ? null : RewardReceiptResponse.builder()
						.ticketCount(result.getReward().getTicketCount())
						.grade(result.getReward().getGrade().name())
						.grantedAt(result.getReward().getGrantedAt())
						.expiresAt(result.getReward().getExpiresAt())
						.build())
				.createdAt(result.getReceivedAt())
				.build();
	}

	/**
	 * 응모권 지급 결과. 값은 응모권 서비스가 확정한 것을 그대로 담는다.
	 *
	 * @param grade 지급한 응모권 등급({@code BRONZE}·{@code SILVER}·{@code GOLD})
	 */
	@Builder
	public record RewardReceiptResponse(long ticketCount, String grade, Instant grantedAt, Instant expiresAt) {
	}
}
