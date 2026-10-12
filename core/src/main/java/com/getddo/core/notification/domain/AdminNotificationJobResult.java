package com.getddo.core.notification.domain;

import java.util.Objects;

import lombok.Getter;

/** Service가 조회 시점에 판단한 복구 가능 여부와 작업 데이터를 함께 반환한다. */
@Getter
public final class AdminNotificationJobResult {
	private final AdminNotificationJob job;
	private final boolean retryable;

	/** 작업 데이터와 Service의 복구 가능 여부 판단을 변경 불가능하게 보관한다. */
	public AdminNotificationJobResult(AdminNotificationJob job, boolean retryable) {
		this.job = Objects.requireNonNull(job, "job");
		this.retryable = retryable;
	}
}
