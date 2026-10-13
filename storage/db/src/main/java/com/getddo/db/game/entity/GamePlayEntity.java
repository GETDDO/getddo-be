package com.getddo.db.game.entity;

import java.time.Instant;
import java.util.UUID;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.getddo.db.common.entity.BaseEntity;

/** 게임 저장 모델. 참조 대상 탐색 없이 사용하는 외래 키는 UUID로 보관한다. */
@Getter
@Entity
@Table(name = "game_plays")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GamePlayEntity extends BaseEntity {

	@Column(name = "game_id", nullable = false, updatable = false, length = 16)
	private UUID gameId;

	@Column(name = "user_id", nullable = false, updatable = false, length = 16)
	private UUID userId;

	@Column(name = "rule_version", nullable = false, updatable = false, length = 30)
	private String ruleVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "rules_snapshot", updatable = false, columnDefinition = "json")
	private String rulesSnapshot;

	@Column(name = "play_token_hash", updatable = false, length = 64)
	private String playTokenHash;

	@Column(name = "generator_version", updatable = false, length = 50)
	private String generatorVersion;

	@Column(name = "generation_seed", updatable = false)
	private Long generationSeed;

	@Column(name = "score")
	private Long score;

	@Column(name = "result_hash", length = 64)
	private String resultHash;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Builder
	private GamePlayEntity(UUID gameId,
			UUID userId,
			String ruleVersion,
			String rulesSnapshot,
			String playTokenHash,
			String generatorVersion,
			Long generationSeed,
			Long score,
			String resultHash,
			Instant completedAt) {
		this.gameId = Objects.requireNonNull(gameId, "gameId");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.ruleVersion = Objects.requireNonNull(ruleVersion, "ruleVersion");
		this.rulesSnapshot = rulesSnapshot;
		this.playTokenHash = playTokenHash;
		this.generatorVersion = generatorVersion;
		this.generationSeed = generationSeed;
		this.score = score;
		this.resultHash = resultHash;
		this.completedAt = completedAt;
	}

	/** 확정 전 행에만 도메인에서 검증한 최초 결과를 적용한다. */
	public void complete(Long score, String resultHash, Instant completedAt) {
		if (this.completedAt != null) {
			throw new IllegalStateException("이미 확정된 플레이입니다.");
		}
		this.score = score;
		this.resultHash = resultHash;
		this.completedAt = completedAt;
	}
}
