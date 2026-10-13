package com.getddo.core.drawing.domain;

import lombok.Getter;
import com.getddo.core.common.exception.ErrorCode;

/** 이번 호출이 새 시도를 얻었는지와 저장 상태를 구분한다. */
@Getter
public final class DrawExecutionClaim {
	private final DrawExecutionState state;
	private final boolean claimed;
	private final ErrorCode rejectionCode;
	/** 거절 코드 없이 저장 상태와 이번 호출의 새 시도 획득 여부를 전달한다. */
	public DrawExecutionClaim(DrawExecutionState state, boolean claimed) { this(state, claimed, null); }
	/** 복구 기록을 먼저 커밋하고 HTTP 거절은 트랜잭션 밖으로 전달한다. */
	public DrawExecutionClaim(DrawExecutionState state, boolean claimed, ErrorCode rejectionCode) {
		this.state = state; this.claimed = claimed; this.rejectionCode = rejectionCode;
	}
}
