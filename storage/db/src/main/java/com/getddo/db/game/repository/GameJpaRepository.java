package com.getddo.db.game.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.game.entity.GameEntity;

public interface GameJpaRepository extends JpaRepository<GameEntity, UUID> {

	Optional<GameEntity> findByCode(String code);

	List<GameEntity> findByActiveTrueOrderByCodeAsc();
}
