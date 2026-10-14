package com.getddo.api.drawing.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.getddo.core.drawing.domain.DrawInspectionView;
import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.domain.DrawType;

/** 실행 종류는 실제 저장 값이다. 자동·수동 실행 행위를 추정하지 않는다. */
public record DrawRunResponse(UUID id, UUID eventId, int runNumber, DrawType drawType, DrawRunStatus status,
		UUID originalDrawId, Instant startedAt, Instant confirmedAt, Instant createdAt) {
	public static DrawRunResponse from(DrawInspectionView.Run run) {
		return new DrawRunResponse(run.getId(), run.getEventId(), run.getRunNumber(), run.getDrawType(),
				run.getStatus(), run.getOriginalDrawId(), run.getStartedAt(), run.getConfirmedAt(), run.getCreatedAt());
	}
}
