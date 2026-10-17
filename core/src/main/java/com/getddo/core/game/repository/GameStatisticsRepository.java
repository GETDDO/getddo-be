package com.getddo.core.game.repository;

import java.util.UUID;

import com.getddo.core.game.domain.GameStatistics;

/** 완료 처리 트랜잭션은 플레이 → 사용자별 게임 통계 순서로 잠근다. */
public interface GameStatisticsRepository {
	/** 없으면 생성하며, 중복 생성 경합은 호출자가 전체 롤백 후 새 트랜잭션에서 재처리한다. */
	void lockOrCreate(UUID userId, UUID gameId);
	void addScore(UUID userId, UUID gameId, long score);
	GameStatistics find(UUID userId, UUID gameId);
}
