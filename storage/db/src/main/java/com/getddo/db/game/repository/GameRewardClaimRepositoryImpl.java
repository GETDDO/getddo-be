package com.getddo.db.game.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.getddo.core.game.domain.GameRewardClaim;
import com.getddo.core.game.repository.GameRewardClaimRepository;
import com.getddo.db.common.util.UuidBinary;
import com.getddo.db.game.mapper.GameResultMapper;

/** 정책 테이블은 기존 공용 계약대로 읽기만 하고 게임 청구만 저장한다. */
@Repository
@RequiredArgsConstructor
public class GameRewardClaimRepositoryImpl implements GameRewardClaimRepository {
	private final GameRewardClaimJpaRepository repository;
	private final GameResultMapper mapper;
	private final JdbcTemplate jdbc;

	@Override
	public Optional<GameRewardClaim> findByPlay(UUID playId) {
		return repository.findByGamePlayId(playId).map(mapper::toDomain);
	}

	@Override
	public Optional<GameRewardClaim> findByDate(UUID userId, UUID gameId, LocalDate date) {
		return repository.findByUserIdAndGameIdAndRewardDate(userId, gameId, date).map(mapper::toDomain);
	}

	@Override
	public GameRewardClaim insert(GameRewardClaim claim) {
		return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(claim)));
	}

	@Override
	public UUID requirePolicy(UUID gameId, Instant at) {
		LocalDateTime utc = LocalDateTime.ofInstant(at, ZoneOffset.UTC);
		List<UUID> policies = jdbc.query("""
			select id, reward_ticket_count from reward_policies
			where reward_type = 'GAME' and game_id = ? and effective_from <= ?
			and (effective_until is null or effective_until > ?) order by effective_from desc limit 1
			""", (row, index) -> {
			if (row.getInt("reward_ticket_count") != 1) throw new IllegalStateException("게임 보상 수량은 한 장이어야 합니다.");
			return UuidBinary.fromBytes(row.getBytes("id"));
		}, UuidBinary.toBytes(gameId), utc, utc);
		return policies.stream().findFirst().orElseThrow(() -> new IllegalStateException("적용 가능한 게임 보상 정책이 없습니다."));
	}
}
