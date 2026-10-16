package com.getddo.db.game.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.game.entity.UserGameStatsEntity;

public interface UserGameStatsJpaRepository extends JpaRepository<UserGameStatsEntity, UUID> {
	Optional<UserGameStatsEntity> findByUserIdAndGameId(UUID userId, UUID gameId);
}
