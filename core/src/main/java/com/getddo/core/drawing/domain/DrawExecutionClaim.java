package com.getddo.core.drawing.domain;

import lombok.Getter;
import com.getddo.core.common.exception.ErrorCode;

/** 이번 호출이 새 시도를 얻었는지와 저장 상태를 구분한다. */
@Getter
public final class DrawExecutionClaim {
	private final DrawExecutionState state;
	private final boolean claimed;
	private final ErrorCode rejectionCode;
	public DrawExecutionClaim(DrawExecutionState state, boolean claimed) { this(state, claimed, null); }
	/** 복구 기록을 먼저 커밋하고 HTTP 거절은 트랜잭션 밖으로 전달한다. */
	public DrawExecutionClaim(DrawExecutionState state, boolean claimed, ErrorCode rejectionCode) {
		this.state = state; this.claimed = claimed; this.rejectionCode = rejectionCode;
	}
}
