package com.getddo.core.game.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** G05의 본인 통계와 조회 순간의 KST 일일 보상 상태. */
@Getter
public final class MyGameStatistics {
	private final UUID gameId;
	private final long bestScore;
	private final long totalScore;
	private final long validPlayCount;
	private final LocalDate rewardDate;
	private final boolean rewardClaimedToday;
	private final Instant nextResetAt;
	private final Instant serverTime;

	public MyGameStatistics(UUID gameId, GameStatistics statistics, LocalDate rewardDate, boolean rewardClaimedToday,
			Instant nextResetAt, Instant serverTime) {
		this.gameId = Objects.requireNonNull(gameId, "gameId");
		this.bestScore = statistics.getBestScore();
		this.totalScore = statistics.getTotalScore();
		this.validPlayCount = statistics.getValidPlayCount();
		this.rewardDate = Objects.requireNonNull(rewardDate, "rewardDate");
		this.rewardClaimedToday = rewardClaimedToday;
		this.nextResetAt = Objects.requireNonNull(nextResetAt, "nextResetAt");
		this.serverTime = Objects.requireNonNull(serverTime, "serverTime");
	}
}
