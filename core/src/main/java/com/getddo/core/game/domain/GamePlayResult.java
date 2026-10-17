package com.getddo.core.game.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

import com.getddo.core.ticket.domain.GrantResult;

/** 최초 플레이 결과와 그 플레이가 실제로 받은 보상. 다른 플레이의 당일 보상을 붙이지 않는다. */
@Getter
public final class GamePlayResult {
	private final UUID playId;
	private final GameResultStatus status;
	private final Long score;
	private final Instant completedAt;
	private final GrantResult reward;

	private GamePlayResult(UUID playId, GameResultStatus status, Long score, Instant completedAt, GrantResult reward) {
		this.playId = Objects.requireNonNull(playId, "playId");
		this.status = status;
		this.score = score;
		this.completedAt = completedAt;
		this.reward = reward;
	}

	public static GamePlayResult processing(UUID playId) {
		return new GamePlayResult(playId, GameResultStatus.PROCESSING, null, null, null);
	}

	public static GamePlayResult completed(GamePlay play, GrantResult reward) {
		if (!play.isCompleted() || (!play.isValid() && reward != null)) {
			throw new IllegalArgumentException("확정된 플레이와 유효한 보상 연결이 필요합니다.");
		}
		return new GamePlayResult(play.getId(), play.isValid() ? GameResultStatus.VALID : GameResultStatus.INVALID,
				play.getScore(), play.getCompletedAt(), reward);
	}
}
