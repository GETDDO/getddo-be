package com.getddo.core.drawing.service;

import java.time.Instant;

import com.getddo.core.drawing.domain.DrawSnapshotSource;
import com.getddo.core.drawing.domain.DrawType;
import com.getddo.core.event.domain.EventStatus;

/** 소유권 획득·입력 준비·확정에서 같은 이벤트 실행 조건을 적용한다. */
final class DrawExecutionEligibility {
	/** 상태 없는 공통 실행 조건 검사만 제공하므로 인스턴스를 만들지 않는다. */
	private DrawExecutionEligibility() {
	}

	/** 삭제 여부와 마감 후 5분 경계를 확인하고, 최초는 OPEN/CLOSED·재추첨은 REDRAWING에서만 새 실행 시도를 허용한다. */
	static boolean isExecutable(DrawSnapshotSource.Event event, DrawType type, Instant now) {
		boolean allowedStatus = type == DrawType.INITIAL
				? event.status() == EventStatus.OPEN || event.status() == EventStatus.CLOSED
				: type == DrawType.REPLACEMENT && event.status() == EventStatus.REDRAWING;
		return !event.deleted() && allowedStatus && !now.isBefore(event.endsAt().plusSeconds(300));
	}
}
