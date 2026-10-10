package com.getddo.core.notification.domain;

/** 알림 생성 작업의 상태다. 사용자 읽음·모의 발송 상태와 구분한다. */
public enum NotificationJobStatus {
	PENDING,
	PROCESSING,
	COMPLETED,
	FAILED
}
