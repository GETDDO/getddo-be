package com.getddo.core.drawing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.getddo.core.drawing.domain.UserDrawResult.MaskedWinner;

/** 기존 응모와 실제 공개에 포함된 결과만 읽는다. */
public interface UserDrawResultRepository {
	boolean hasEntered(UUID eventId, UUID userId);
	boolean hasNoEntriesRun(UUID eventId);
	List<MaskedWinner> findWinners(UUID publicationId);
	Optional<UUID> findWonPrize(UUID publicationId, UUID userId);
}
