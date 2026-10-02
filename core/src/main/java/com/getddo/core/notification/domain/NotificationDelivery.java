package com.getddo.core.notification.domain;

import java.util.UUID;

import lombok.Value;

/** 이미 생성된 알림의 모의 발송 입력이다. 읽음 상태를 포함하거나 변경하지 않는다. */
@Value
public class NotificationDelivery {
	UUID id;
	UUID userId;
	String title;
	String body;
	String linkUrl;
	int attemptCount;
}
