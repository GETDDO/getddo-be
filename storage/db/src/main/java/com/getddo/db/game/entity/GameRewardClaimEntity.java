package com.getddo.db.game.entity;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.getddo.db.common.entity.BaseEntity;

/** 기존 game_reward_claims 저장 모델. ID・생성 시각은 공통 Auditing을 사용한다. */
@Entity
@Getter
@Table(name = "game_reward_claims")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameRewardClaimEntity extends BaseEntity {
	@Column(nullable = false, updatable = false) private UUID userId;
	@Column(nullable = false, updatable = false) private UUID gameId;
	@Column(nullable = false, updatable = false) private UUID gamePlayId;
	@Column(nullable = false, updatable = false) private UUID rewardPolicyId;
	@Column(nullable = false, updatable = false) private LocalDate rewardDate;
	@Column(nullable = false, updatable = false, length = 160) private String sourceKey;
	@Column(nullable = false, updatable = false) private int ticketCount;

	@Builder
	private GameRewardClaimEntity(UUID userId, UUID gameId, UUID gamePlayId, UUID rewardPolicyId,
			LocalDate rewardDate, String sourceKey, int ticketCount) {
		this.userId = userId;
		this.gameId = gameId;
		this.gamePlayId = gamePlayId;
		this.rewardPolicyId = rewardPolicyId;
		this.rewardDate = rewardDate;
		this.sourceKey = sourceKey;
		this.ticketCount = ticketCount;
	}
}
