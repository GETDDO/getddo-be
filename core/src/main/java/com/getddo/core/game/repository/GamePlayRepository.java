package com.getddo.core.game.repository;

import java.util.Optional;
import java.util.UUID;

import com.getddo.core.game.domain.GamePlay;

public interface GamePlayRepository {

	GamePlay insert(GamePlay play);

	Optional<GamePlay> find(UUID playId, UUID userId, UUID gameId);

	/** 결과·통계·청구·지급을 함께 커밋하는 호출자의 트랜잭션에서 조회한다. */
	Optional<GamePlay> findForUpdate(UUID playId, UUID userId, UUID gameId);

	/** 결과 제출용 즉시 잠금. 실제 잠금 경합은 GamePlayBusyException으로 구분한다. */
	Optional<GamePlay> findForUpdateNowait(UUID playId, UUID userId, UUID gameId);

	/** 최초 확정만 반영한다. 같은 입력은 최초 결과를 반환하며 변경된 입력은 거절한다. */
	GamePlay complete(GamePlay play);
}
