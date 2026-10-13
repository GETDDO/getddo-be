package com.getddo.core.game.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.getddo.core.game.domain.Game;

public interface GameRepository {

	Game insert(Game game);

	Optional<Game> findById(UUID id);

	Optional<Game> findByCode(String code);

	List<Game> findActive();
}
