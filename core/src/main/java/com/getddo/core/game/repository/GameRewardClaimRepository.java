package com.getddo.core.game.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.getddo.core.game.domain.GameRewardClaim;

/** 게임 청구만 소유한다. 응모권·등급·만료는 기존 지급 서비스가 소유한다. */
public interface GameRewardClaimRepository {
	Optional<GameRewardClaim> findByPlay(UUID playId);
	Optional<GameRewardClaim> findByDate(UUID userId, UUID gameId, LocalDate date);
	GameRewardClaim insert(GameRewardClaim claim);
	/** 해당 시각에 유효한 한 장 지급 정책. 없거나 손상됐으면 성공 보상으로 처리하지 않는다. */
	UUID requirePolicy(UUID gameId, Instant at);
}
