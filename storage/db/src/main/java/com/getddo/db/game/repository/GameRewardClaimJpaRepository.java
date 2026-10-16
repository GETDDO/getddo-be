package com.getddo.db.game.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.game.entity.GameRewardClaimEntity;

public interface GameRewardClaimJpaRepository extends JpaRepository<GameRewardClaimEntity, UUID> {
	Optional<GameRewardClaimEntity> findByGamePlayId(UUID playId);
	Optional<GameRewardClaimEntity> findByUserIdAndGameIdAndRewardDate(UUID userId, UUID gameId, LocalDate date);
}
