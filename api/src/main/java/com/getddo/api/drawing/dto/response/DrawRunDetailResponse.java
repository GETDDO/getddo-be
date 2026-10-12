package com.getddo.api.drawing.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import com.getddo.core.drawing.domain.*;

/** 승인된 저장 정보 응답. 미확정 실패·취소 상세를 빈 값으로 가장하지 않는다. */
public record DrawRunDetailResponse(UUID id, UUID eventId, int runNumber, DrawType drawType,
		DrawRunStatus status, UUID originalDrawId, Instant startedAt, Instant confirmedAt, Instant createdAt,
		String algorithmVersion, DrawRulesResponse rulesSnapshot, Instant snapshotFixedAt, List<DrawResultResponse> results) {
	public DrawRunDetailResponse { results = List.copyOf(results); }
	public static DrawRunDetailResponse from(DrawRunDetailView detail) {
		DrawRunView run = detail.getRun();
		Map<UUID, Integer> ranks = new HashMap<>();
		if (run.getRules() != null) {
			for (DrawSnapshotSource.Prize prize : run.getRules().prizes()) ranks.put(prize.id(), prize.rank());
		}
		return new DrawRunDetailResponse(run.getId(), run.getEventId(), run.getRunNumber(), run.getDrawType(),
				run.getStatus(), run.getOriginalDrawId(), run.getStartedAt(), run.getConfirmedAt(), run.getCreatedAt(),
				run.getAlgorithmVersion(), DrawRulesResponse.from(run), run.getSnapshotFixedAt(), detail.getResults().stream()
				.map(result -> DrawResultResponse.from(result, ranks.get(result.getPrizeId()), result.getUserId())).toList());
	}
}
