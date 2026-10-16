package com.getddo.core.game.domain;

import lombok.Getter;

/** 유효 완료만 한 번 반영한 사용자별 게임 점수 집계. */
@Getter
public final class GameStatistics {
	private final long bestScore;
	private final long totalScore;
	private final long validPlayCount;

	public GameStatistics(long bestScore, long totalScore, long validPlayCount) {
		if (bestScore < 0 || totalScore < bestScore || validPlayCount < 0) {
			throw new IllegalArgumentException("게임 점수 집계가 올바르지 않습니다.");
		}
		this.bestScore = bestScore;
		this.totalScore = totalScore;
		this.validPlayCount = validPlayCount;
	}
}
