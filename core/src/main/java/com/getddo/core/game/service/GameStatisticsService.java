package com.getddo.core.game.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.game.domain.MyGameStatistics;
import com.getddo.core.game.exception.GameErrorCode;
import com.getddo.core.game.repository.GameRepository;
import com.getddo.core.game.repository.GameRewardClaimRepository;
import com.getddo.core.game.repository.GameStatisticsRepository;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;

/** 과거 통계는 게임 비활성화 뒤에도 읽으며 조회로 청구·지급·통계 행을 생성하지 않는다. */
@Service
@RequiredArgsConstructor
public class GameStatisticsService {
	private final GameRepository games;
	private final GameStatisticsRepository statistics;
	private final GameRewardClaimRepository claims;
	private final TimeProvider time;

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public MyGameStatistics find(User user, UUID gameId) {
		if (user == null || user.getRole() != UserRole.USER || user.getStatus() != UserStatus.ACTIVE) {
			throw new BusinessException(GameErrorCode.RESULT_ACCESS_DENIED);
		}
		games.findById(gameId).orElseThrow(() -> new BusinessException(GameErrorCode.GAME_NOT_FOUND));
		Instant now = time.now();
		LocalDate date = time.businessDate(now);
		Instant reset = date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
		return new MyGameStatistics(gameId, statistics.find(user.getId(), gameId), date,
				claims.findByDate(user.getId(), gameId, date).isPresent(), reset, now);
	}
}
