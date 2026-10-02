package com.getddo.core.notification.domain;

import java.util.UUID;

import lombok.Value;

/** 작업 ID와 선점 차수다. 이전 처리자의 늦은 저장을 현재 차수와 구분한다. */
@Value
public class NotificationJob {
	UUID id;
	int attemptCount;
}
