package com.getddo.core.drawing.service;

import java.time.Instant;

import com.getddo.core.drawing.domain.DrawSnapshotSource;
import com.getddo.core.drawing.domain.DrawType;
import com.getddo.core.event.domain.EventStatus;

/** 소유권 획득·입력 준비·확정에서 같은 이벤트 실행 조건을 적용한다. */
final class DrawExecutionEligibility {
	private DrawExecutionEligibility() {
	}

	static boolean isExecutable(DrawSnapshotSource.Event event, DrawType type, Instant now) {
		boolean allowedStatus = type == DrawType.INITIAL
				? event.status() == EventStatus.OPEN || event.status() == EventStatus.CLOSED
				: type == DrawType.REPLACEMENT && event.status() == EventStatus.REDRAWING;
		return !event.deleted() && allowedStatus && !now.isBefore(event.endsAt().plusSeconds(300));
	}
}
