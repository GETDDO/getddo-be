package com.getddo.core.game.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.game.domain.ColorWordValidationResult;
import com.getddo.core.game.domain.GamePlay;
import com.getddo.core.game.domain.GamePlayCredentials;
import com.getddo.core.game.domain.GamePlayResult;
import com.getddo.core.game.domain.GameResultSubmission;
import com.getddo.core.game.domain.GameRewardClaim;
import com.getddo.core.game.exception.GameErrorCode;
import com.getddo.core.game.repository.GamePlayRepository;
import com.getddo.core.game.repository.GameRewardClaimRepository;
import com.getddo.core.game.repository.GameStatisticsRepository;
import com.getddo.core.ticket.domain.GrantCommand;
import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.service.TicketGrantService;

/** 결과·통계·일일 청구·응모권 지급을 한 번에 확정한다. 실패는 전체 롤백한다. */
@Service
@RequiredArgsConstructor
public class GamePlayResultRecorder {
	private final GamePlayRepository plays;
	private final ColorWordValidator validator;
	private final GameStatisticsRepository statistics;
	private final GameRewardClaimRepository claims;
	private final TicketGrantService grants;
	private final TimeProvider time;

	/** 플레이 → 사용자별 게임 통계 → 청구 → 응모권 순서. 호출자는 별도 Bean이다. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public GamePlayResult record(UUID userId, GameResultSubmission submission) {
		GamePlay play = plays.findForUpdateNowait(submission.getPlayId(), userId, submission.getGameId())
				.filter(found -> GamePlayCredentials.matches(found, userId, submission.getGameId(), submission.getPlayToken()))
				.orElseThrow(() -> new BusinessException(GameErrorCode.PLAY_NOT_FOUND));
		if (play.isCompleted()) {
			if (!Objects.equals(play.getResultHash(), submission.getResultHash())) {
				throw new BusinessException(GameErrorCode.RESULT_CONFLICT);
			}
			return GamePlayResult.completed(play, previousReward(play));
		}
		ColorWordValidationResult judged = validator.validate(play, submission.getAnswers());
		if (judged.isValid()) statistics.lockOrCreate(userId, play.getGameId());
		Instant completedAt = time.now().truncatedTo(ChronoUnit.MICROS);
		GamePlay completed = plays.complete(play.complete(judged.getScore(), submission.getResultHash(), completedAt));
		GrantResult reward = null;
		if (completed.isValid()) {
			statistics.addScore(userId, completed.getGameId(), completed.getScore());
			if (completed.getScore() >= 50) reward = claimAndGrant(completed);
		}
		return GamePlayResult.completed(completed, reward);
	}

	private GrantResult claimAndGrant(GamePlay completed) {
		LocalDate date = time.businessDate(completed.getCompletedAt());
		// 다른 플레이가 이미 지급받은 날이면 이번 플레이의 보상은 null로 고정한다.
		if (claims.findByDate(completed.getUserId(), completed.getGameId(), date).isPresent()) return null;
		GameRewardClaim claim = claims.insert(GameRewardClaim.of(completed,
				claims.requirePolicy(completed.getGameId(), completed.getCompletedAt()), date));
		return grants.grant(new GrantCommand(completed.getUserId(), source(claim), 1, "글자색깔 맞추기 일일 보상"));
	}

	private GrantResult previousReward(GamePlay play) {
		return claims.findByPlay(play.getId()).map(claim -> grants.findGrant(source(claim))
				.orElseThrow(() -> new IllegalStateException("확정된 게임 청구의 지급 결과가 없습니다."))).orElse(null);
	}

	private static GrantSource source(GameRewardClaim claim) {
		return new GrantSource(GrantSourceType.GAME, claim.getId());
	}
}
