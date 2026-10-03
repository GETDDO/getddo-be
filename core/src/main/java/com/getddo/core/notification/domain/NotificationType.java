package com.getddo.core.notification.domain;

/** 기존 알림 스키마가 지원하는 발생 사유다. */
public enum NotificationType {
	EVENT_START,
	RESULT_PUBLISHED,
	ENTRY_EXCLUDED,
	TICKET_REVOKED,
	EVENT_SCHEDULE_CHANGED,
	EVENT_CANCELED,
	RESULT_CHANGED
}
