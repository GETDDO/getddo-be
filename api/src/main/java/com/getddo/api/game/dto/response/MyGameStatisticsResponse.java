package com.getddo.api.game.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;

import com.getddo.core.game.domain.MyGameStatistics;

/** G05 초안에서 채택한 공개 필드만 반환한다. */
@Builder
public record MyGameStatisticsResponse(UUID gameId, long bestScore, long totalScore, long validPlayCount,
		LocalDate rewardDate, boolean rewardClaimedToday, Instant nextResetAt, Instant serverTime) {
	public static MyGameStatisticsResponse from(MyGameStatistics stats) {
		return MyGameStatisticsResponse.builder().gameId(stats.getGameId()).bestScore(stats.getBestScore())
				.totalScore(stats.getTotalScore()).validPlayCount(stats.getValidPlayCount()).rewardDate(stats.getRewardDate())
				.rewardClaimedToday(stats.isRewardClaimedToday()).nextResetAt(stats.getNextResetAt())
				.serverTime(stats.getServerTime()).build();
	}
}
