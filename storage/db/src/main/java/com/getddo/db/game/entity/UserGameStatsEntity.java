package com.getddo.db.game.entity;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.getddo.db.common.entity.BaseUpdatableEntity;

/** 사용자별 게임 통계 행. 호출자는 쓰기 잠금 후 유효 완료를 한 번만 더한다. */
@Getter
@Entity
@Table(name = "user_game_stats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserGameStatsEntity extends BaseUpdatableEntity {

	@Column(name = "user_id", nullable = false, updatable = false, length = 16)
	private UUID userId;

	@Column(name = "game_id", nullable = false, updatable = false, length = 16)
	private UUID gameId;

	@Column(name = "best_score", nullable = false)
	private long bestScore;

	@Column(name = "total_score", nullable = false)
	private long totalScore;

	@Column(name = "valid_play_count", nullable = false)
	private long validPlayCount;

	public UserGameStatsEntity(UUID userId, UUID gameId) {
		this.userId = Objects.requireNonNull(userId, "userId");
		this.gameId = Objects.requireNonNull(gameId, "gameId");
	}

	public void addScore(long score) {
		if (score < 0) {
			throw new IllegalArgumentException("음수 점수는 집계하지 않습니다.");
		}
		bestScore = Math.max(bestScore, score);
		totalScore = Math.addExact(totalScore, score);
		validPlayCount = Math.addExact(validPlayCount, 1);
	}
}
