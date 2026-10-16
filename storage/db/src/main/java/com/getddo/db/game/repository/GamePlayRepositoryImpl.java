package com.getddo.db.game.repository;

import java.util.Optional;
import java.util.UUID;
import java.sql.SQLException;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.game.domain.GamePlay;
import com.getddo.core.game.repository.GamePlayRepository;
import com.getddo.db.game.entity.GamePlayEntity;
import com.getddo.db.game.mapper.GamePlayMapper;
import com.getddo.db.common.util.UuidBinary;
import com.getddo.core.game.exception.GamePlayBusyException;

@Repository
@RequiredArgsConstructor
public class GamePlayRepositoryImpl implements GamePlayRepository {

	private final GamePlayJpaRepository playJpaRepository;
	private final GamePlayMapper mapper;
	private final JdbcTemplate jdbc;
	private final EntityManager entityManager;

	@Override
	public GamePlay insert(GamePlay play) {
		if (play.getId() != null || play.isCompleted() || !play.hasVerificationContext()
				|| play.getScore() != null || play.getResultHash() != null) {
			throw new IllegalArgumentException("검증 기준을 갖춘 새 플레이만 발급할 수 있습니다.");
		}
		return mapper.toDomain(playJpaRepository.save(mapper.toEntity(play)));
	}

	@Override
	public Optional<GamePlay> find(UUID playId, UUID userId, UUID gameId) {
		return playJpaRepository.findByIdAndUserIdAndGameId(playId, userId, gameId).map(mapper::toDomain);
	}

	@Override
	public Optional<GamePlay> findForUpdate(UUID playId, UUID userId, UUID gameId) {
		return playJpaRepository.readByIdAndUserIdAndGameId(playId, userId, gameId).map(mapper::toDomain);
	}

	@Override
	public Optional<GamePlay> findForUpdateNowait(UUID playId, UUID userId, UUID gameId) {
		try {
			boolean exists = !jdbc.queryForList("""
				select id from game_plays where id = ? and user_id = ? and game_id = ? for update nowait
				""", UuidBinary.toBytes(playId), UuidBinary.toBytes(userId), UuidBinary.toBytes(gameId)).isEmpty();
			if (!exists) return Optional.empty();
			GamePlayEntity entity = entityManager.getReference(GamePlayEntity.class, playId);
			entityManager.refresh(entity);
			return Optional.of(mapper.toDomain(entity));
		} catch (DataAccessException failure) {
			for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
				if (cause instanceof SQLException sql && sql.getErrorCode() == 3572) {
					throw new GamePlayBusyException(failure);
				}
			}
			throw failure;
		}
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public GamePlay complete(GamePlay play) {
		if (!play.isCompleted()) {
			throw new IllegalArgumentException("서버 판정이 완료된 결과가 필요합니다.");
		}
		GamePlayEntity entity = playJpaRepository
				.readByIdAndUserIdAndGameId(play.getId(), play.getUserId(), play.getGameId()).orElseThrow();
		GamePlay stored = mapper.toDomain(entity);
		GamePlay completed = stored.complete(play.getScore(), play.getResultHash(), play.getCompletedAt());
		if (!stored.isCompleted()) {
			entity.complete(completed.getScore(), completed.getResultHash(), completed.getCompletedAt());
		}
		return completed;
	}
}
