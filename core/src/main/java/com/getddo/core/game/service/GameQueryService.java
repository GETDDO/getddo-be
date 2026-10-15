package com.getddo.core.game.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.game.domain.Game;
import com.getddo.core.game.domain.GameDetailResult;
import com.getddo.core.game.exception.GameErrorCode;
import com.getddo.core.game.repository.GameRepository;

/** 사용자용 게임 목록·상세 조회. 시작·결과·통계·보상을 생성하지 않는다. */
@Service
@RequiredArgsConstructor
public class GameQueryService {

	private final GameRepository games;
	private final List<GamePlayStartHandler> handlers;

	/** 시작 지원 여부와 무관하게 활성 게임 전체를 기존 저장소의 code 오름차순으로 반환한다. */
	@Transactional(readOnly = true)
	public List<Game> findGames() {
		return games.findActive();
	}

	/**
	 * 비활성·없는 게임은 같은 404로 숨긴다. 활성 게임의 미지원 규칙은 409로 구분한다.
	 * 시작 처리기의 순수 규칙 준비만 재사용하며 토큰·seed 발급이나 플레이 저장은 호출하지 않는다.
	 */
	@Transactional(readOnly = true)
	public GameDetailResult findGame(UUID gameId) {
		Game game = games.findById(Objects.requireNonNull(gameId, "gameId"))
				.filter(Game::isActive)
				.orElseThrow(() -> new BusinessException(GameErrorCode.GAME_NOT_FOUND));
		List<GamePlayStartHandler> supported = handlers.stream().filter(handler -> handler.supports(game)).toList();
		if (supported.isEmpty()) {
			throw new BusinessException(GameErrorCode.GAME_DETAIL_NOT_AVAILABLE);
		}
		if (supported.size() != 1) {
			throw new IllegalStateException("게임 상세 규칙 처리기가 중복 등록되었습니다.");
		}
		return new GameDetailResult(game, supported.getFirst().prepare(game).getRules());
	}
}
