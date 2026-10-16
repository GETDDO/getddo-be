package com.getddo.api.game.dto.response;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;

import com.getddo.core.game.domain.GamePlayResult;
import com.getddo.core.game.domain.GameResultStatus;
import com.getddo.core.ticket.domain.GrantResult;

/** 플레이 최초 결과만 응답하며 토큰・사용자 ID・입력 해시를 노출하지 않는다. */
@Builder
public record GameResultResponse(UUID playId, GameResultStatus status, Long score, Instant completedAt,
		RewardReceipt reward) {
	public static GameResultResponse from(GamePlayResult result) {
		GrantResult grant = result.getReward();
		return GameResultResponse.builder().playId(result.getPlayId()).status(result.getStatus())
				.score(result.getScore()).completedAt(result.getCompletedAt())
				.reward(grant == null ? null : RewardReceipt.builder().ticketCount(grant.getQuantity())
						.grade(grant.getGrade().name()).grantedAt(grant.getGrantedAt()).expiresAt(grant.getExpiresAt()).build())
				.build();
	}

	@Builder
	public record RewardReceipt(long ticketCount, String grade, Instant grantedAt, Instant expiresAt) { }
}
