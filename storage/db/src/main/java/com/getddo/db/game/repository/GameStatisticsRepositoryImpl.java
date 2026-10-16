package com.getddo.db.game.repository;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.game.domain.GameStatistics;
import com.getddo.core.game.repository.GameStatisticsRepository;
import com.getddo.db.game.entity.UserGameStatsEntity;
import com.getddo.db.game.mapper.GameResultMapper;

@Repository
@RequiredArgsConstructor
public class GameStatisticsRepositoryImpl implements GameStatisticsRepository {
	private final UserGameStatsJpaRepository repository;
	private final EntityManager entityManager;
	private final GameResultMapper mapper;

	@Override
	public void lockOrCreate(UUID userId, UUID gameId) {
		UserGameStatsEntity found = repository.findByUserIdAndGameId(userId, gameId).orElse(null);
		if (found == null) {
			// 없는 범위를 먼저 잠그지 않는다. 생성 UNIQUE 경합은 상위 서비스가 새 트랜잭션에서 재시도한다.
			repository.saveAndFlush(new UserGameStatsEntity(userId, gameId));
		} else {
			entityManager.refresh(found, LockModeType.PESSIMISTIC_WRITE);
		}
	}

	@Override
	public void addScore(UUID userId, UUID gameId, long score) {
		UserGameStatsEntity stats = repository.findByUserIdAndGameId(userId, gameId).orElseThrow();
		stats.addScore(score);
	}

	@Override
	public GameStatistics find(UUID userId, UUID gameId) {
		return repository.findByUserIdAndGameId(userId, gameId).map(mapper::toDomain)
				.orElseGet(() -> new GameStatistics(0, 0, 0));
	}
}
