package com.getddo.core.drawing.domain;

import java.util.UUID;

/** 취소 대상을 식별하고 이벤트 → 실행 잠금 순서를 정하기 위한 조회 값. */
public record CancellationTarget(UUID resultId, UUID runId, UUID eventId, DrawRunStatus status,
		DrawSelectionResultType resultType) { }
