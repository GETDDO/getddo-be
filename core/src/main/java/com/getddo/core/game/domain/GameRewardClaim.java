package com.getddo.core.game.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** 사용자·게임·KST 보상일당 한 장인 지급 근거. 지급 서비스에는 서버가 저장한 청구 ID만 전달한다. */
@Getter
public final class GameRewardClaim {
	private final UUID id;
	private final UUID userId;
	private final UUID gameId;
	private final UUID gamePlayId;
	private final UUID rewardPolicyId;
	private final LocalDate rewardDate;
	private final String sourceKey;
	private final int ticketCount;
	private final Instant createdAt;

	public GameRewardClaim(UUID id, UUID userId, UUID gameId, UUID gamePlayId, UUID rewardPolicyId,
			LocalDate rewardDate, String sourceKey, int ticketCount, Instant createdAt) {
		this.id = id;
		this.userId = Objects.requireNonNull(userId, "userId");
		this.gameId = Objects.requireNonNull(gameId, "gameId");
		this.gamePlayId = Objects.requireNonNull(gamePlayId, "gamePlayId");
		this.rewardPolicyId = Objects.requireNonNull(rewardPolicyId, "rewardPolicyId");
		this.rewardDate = Objects.requireNonNull(rewardDate, "rewardDate");
		this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
		if (ticketCount != 1 || !sourceKey.equals(sourceKey(gameId, rewardDate))) {
			throw new IllegalArgumentException("게임별 일일 보상은 한 장이며 보상일 식별 키가 일치해야 합니다.");
		}
		this.ticketCount = ticketCount;
		this.createdAt = createdAt;
	}

	public static GameRewardClaim of(GamePlay play, UUID policyId, LocalDate date) {
		if (!play.isValid()) throw new IllegalArgumentException("유효한 완료 플레이가 필요합니다.");
		return new GameRewardClaim(null, play.getUserId(), play.getGameId(), play.getId(), policyId, date,
				sourceKey(play.getGameId(), date), 1, null);
	}

	private static String sourceKey(UUID gameId, LocalDate date) {
		return "game:" + gameId + ":" + date;
	}
}
