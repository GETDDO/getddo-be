package com.getddo.core.notification.domain;

import java.util.Objects;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 이미 생성된 알림의 모의 발송 입력이다. 읽음 상태를 포함하거나 변경하지 않는다. */
@Getter
@EqualsAndHashCode
public final class NotificationDelivery {
	private final UUID id;
	private final UUID userId;
	private final String title;
	private final String body;
	private final String linkUrl;
	private final int attemptCount;

	/** 필수 알림·사용자 ID를 검증하고 불변 발송 입력을 저장한다. */
	public NotificationDelivery(UUID id, UUID userId, String title, String body, String linkUrl, int attemptCount) {
		this.id = Objects.requireNonNull(id, "id");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.title = title;
		this.body = body;
		this.linkUrl = linkUrl;
		this.attemptCount = attemptCount;
	}
}
