package com.getddo.core.drawing.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.getddo.core.drawing.domain.DrawConfirmation;
import com.getddo.core.drawing.domain.DrawSelection;
import com.getddo.core.drawing.domain.DrawSnapshot;

/** 이벤트 선잠금을 보유한 서비스의 같은 트랜잭션에서 결과를 확정한다. */
public interface DrawResultRepository {
	Optional<UUID> findEventId(UUID runId);
	Optional<DrawSnapshot> lockSnapshot(UUID runId);
	void validateRelations(DrawSnapshot snapshot);
	boolean hasResults(UUID runId);
	List<DrawSelection> findCurrentAwards(UUID eventId);
	DrawConfirmation findConfirmed(DrawSnapshot snapshot);
	DrawConfirmation saveConfirmed(DrawSnapshot snapshot, List<DrawSelection> selections, Instant confirmedAt);
}
