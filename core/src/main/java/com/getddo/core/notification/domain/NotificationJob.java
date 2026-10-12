package com.getddo.core.notification.domain;

import java.util.Objects;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 작업 ID와 선점 차수다. 이전 처리자의 늦은 저장을 현재 차수와 구분한다. */
@Getter
@EqualsAndHashCode
public final class NotificationJob {
	private final UUID id;
	private final int attemptCount;
	private final int attemptBase;

	/**
	 * 필수 작업 ID를 검증하고 선점 차수를 저장한다. 0은 아직 선점하지 않은 초기 상태다.
	 * @throws NullPointerException 작업 ID가 null인 경우
	 * @throws IllegalArgumentException 선점 차수가 음수인 경우
	 */
	public NotificationJob(UUID id, int attemptCount) {
		this(id, attemptCount, 0);
	}

	/** 누적 차수와 수동 복구 구간의 기준 차수를 보관한다. */
	public NotificationJob(UUID id, int attemptCount, int attemptBase) {
		this.id = Objects.requireNonNull(id, "id");
		if (attemptCount < 0 || attemptBase < 0 || attemptBase > attemptCount) {
			throw new IllegalArgumentException("attemptCount must be non-negative");
		}
		this.attemptCount = attemptCount;
		this.attemptBase = attemptBase;
	}

	/** 현재 수동 복구 구간에서 최초 시도를 포함한 횟수다. */
	public int getCycleAttemptCount() {
		return attemptCount - attemptBase;
	}

}
