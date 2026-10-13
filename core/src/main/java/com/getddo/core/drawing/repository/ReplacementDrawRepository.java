package com.getddo.core.drawing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import com.getddo.core.drawing.domain.CancellationTarget;
import com.getddo.core.drawing.domain.DrawResult;
import com.getddo.core.drawing.domain.DrawSnapshot;

/** GD-138 전용 취소 대상·재추첨 등록·입력 근거 조회. 변경은 이벤트 선잠금 아래 호출한다. */
public interface ReplacementDrawRepository {
	Optional<CancellationTarget> findTarget(UUID resultId);
	boolean hasPendingReplacement(UUID eventId);
	UUID register(UUID eventId);
	void markRedrawing(UUID eventId, Instant changedAt);
	List<DrawResult> findCurrentResults(UUID eventId, DrawSnapshot original);
	List<DrawResult> findCanceledResults(UUID eventId, DrawSnapshot original);
}
