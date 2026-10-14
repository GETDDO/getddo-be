package com.getddo.db.game.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.getddo.db.game.entity.GamePlayEntity;

public interface GamePlayJpaRepository extends JpaRepository<GamePlayEntity, UUID> {

	Optional<GamePlayEntity> findByIdAndUserIdAndGameId(UUID id, UUID userId, UUID gameId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<GamePlayEntity> readByIdAndUserIdAndGameId(UUID id, UUID userId, UUID gameId);
}
