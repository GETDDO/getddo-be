package com.getddo.core.game.service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.game.domain.Game;
import com.getddo.core.game.domain.GamePlay;
import com.getddo.core.game.domain.GamePlayCredentials;
import com.getddo.core.game.domain.GamePlayStartResult;
import com.getddo.core.game.domain.GameStartConfiguration;
import com.getddo.core.game.exception.GameErrorCode;
import com.getddo.core.game.repository.GamePlayRepository;
import com.getddo.core.game.repository.GameRepository;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserStatus;

/** 게임별 시작 규칙을 준비하고 토큰 해시·문제 생성 기준을 하나의 트랜잭션으로 저장한다. */
@Service
@RequiredArgsConstructor
public class GamePlayStartService {

	private final GameRepository games;
	private final GamePlayRepository plays;
	private final List<GamePlayStartHandler> handlers;
	private final SecureRandom random = new SecureRandom();

	/**
	 * 활성 사용자의 새 플레이를 발급한다. 게임별 자격은 확정된 지원 범위에서 적용한다. 시작은 점수·통계·보상을 만들지 않는다.
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
		if (!game.isActive()) {
			throw new BusinessException(GameErrorCode.GAME_NOT_AVAILABLE);
		}
		List<GamePlayStartHandler> supported = handlers.stream().filter(handler -> handler.supports(game)).toList();
		if (supported.isEmpty()) {
			throw new BusinessException(GameErrorCode.GAME_NOT_AVAILABLE);
		}
		if (supported.size() != 1) {
			throw new IllegalStateException("게임 시작 처리기가 중복 등록되었습니다.");
		}
		GameStartConfiguration configuration = supported.getFirst().prepare(game);
		GamePlayCredentials credentials = GamePlayCredentials.issue(random);
		GamePlay saved = plays.insert(GamePlay.start(game, user.getId(), credentials.getPlayTokenHash(),
				configuration.getGeneratorVersion(), credentials.getGenerationSeed()));
		return new GamePlayStartResult(saved, credentials.getPlayToken(), configuration.getRules());
	}
}
