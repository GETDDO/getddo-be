package com.getddo.core.game.service;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.game.domain.ColorWordRules;
import com.getddo.core.game.domain.Game;
import com.getddo.core.game.domain.GamePlay;
import com.getddo.core.game.domain.GamePlayCredentials;
import com.getddo.core.game.domain.GamePlayStartResult;
import com.getddo.core.game.exception.GameErrorCode;
import com.getddo.core.game.repository.GamePlayRepository;
import com.getddo.core.game.repository.GameRepository;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserStatus;

/** 색깔 게임 시작 시 적용 규칙·토큰 해시·문제 생성 기준을 하나의 트랜잭션으로 저장한다. */
@Service
@RequiredArgsConstructor
public class GamePlayStartService {

	private final GameRepository games;
	private final GamePlayRepository plays;
	private final SecureRandom random = new SecureRandom();

	/**
	 * 활성 일반 사용자와 관리자의 새 플레이를 발급한다. 시작은 점수·통계·보상을 만들지 않는다.
	 *
	 * <p>시작 재요청은 새 플레이다. ID와 시작 시각은 공통 Entity의 저장 결과를 사용하고,
	 * 응답 토큰 원문은 저장하지 않는다. 토큰 만료·빈도 제한의 미확정 수치는 임의로 추가하지 않는다.</p>
	 */
	@Transactional
	public GamePlayStartResult start(User user, UUID gameId) {
		if (user == null || user.getStatus() != UserStatus.ACTIVE) {
			throw new BusinessException(GameErrorCode.USER_INACTIVE);
		}
		Game game = games.findById(Objects.requireNonNull(gameId, "gameId"))
				.orElseThrow(() -> new BusinessException(GameErrorCode.GAME_NOT_FOUND));
		if (!game.isActive() || !ColorWordRules.GAME_CODE.equals(game.getCode())
				|| !ColorWordRules.RULE_VERSION.equals(game.getRuleVersion())) {
			throw new BusinessException(GameErrorCode.GAME_NOT_AVAILABLE);
		}
		ColorWordRules rules;
		try {
			rules = ColorWordRules.fromSnapshot(game.getRules());
		} catch (JacksonException | IllegalArgumentException invalidConfiguration) {
			// 예외 종류와 발생 위치만 보존하고 원본 메시지·JSON·원인 체인은 로그에 노출하지 않는다.
			IllegalStateException safeCause = new IllegalStateException(invalidConfiguration.getClass().getName());
			safeCause.setStackTrace(invalidConfiguration.getStackTrace());
			throw new IllegalStateException("저장된 색깔 게임 규칙이 올바르지 않습니다.", safeCause);
		}
		GamePlayCredentials credentials = GamePlayCredentials.issue(random);
		GamePlay saved = plays.insert(GamePlay.start(game, user.getId(), credentials.getPlayTokenHash(),
				ColorWordRules.GENERATOR_VERSION, credentials.getGenerationSeed()));
		return new GamePlayStartResult(saved, credentials.getPlayToken(), rules);
	}
}
