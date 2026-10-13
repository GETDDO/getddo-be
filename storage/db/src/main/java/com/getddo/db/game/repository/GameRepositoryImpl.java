package com.getddo.db.game.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.game.domain.Game;
import com.getddo.core.game.repository.GameRepository;
import com.getddo.db.game.mapper.GameMapper;

@Repository
@RequiredArgsConstructor
public class GameRepositoryImpl implements GameRepository {

	private final GameJpaRepository gameJpaRepository;
	private final GameMapper mapper;

	@Override
	public Game insert(Game game) {
		if (game.getId() != null) {
			throw new IllegalArgumentException("신규 게임만 등록할 수 있습니다.");
		}
		return mapper.toDomain(gameJpaRepository.save(mapper.toEntity(game)));
	}

	@Override
	public Optional<Game> findById(UUID id) {
		return gameJpaRepository.findById(id).map(mapper::toDomain);
	}

	@Override
	public Optional<Game> findByCode(String code) {
		return gameJpaRepository.findByCode(code).map(mapper::toDomain);
	}

	@Override
	public List<Game> findActive() {
		return gameJpaRepository.findByActiveTrueOrderByCodeAsc().stream().map(mapper::toDomain).toList();
	}
}
