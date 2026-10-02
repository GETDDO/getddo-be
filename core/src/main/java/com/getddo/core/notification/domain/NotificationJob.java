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

	/** 필수 작업 ID를 검증하고 선점 차수를 저장한다. */
	public NotificationJob(UUID id, int attemptCount) {
		this.id = Objects.requireNonNull(id, "id");
		this.attemptCount = attemptCount;
	}
}
