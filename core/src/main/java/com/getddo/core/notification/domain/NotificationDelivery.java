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
	private final int attemptBase;

	/**
	 * 필수 식별자·문구를 검증하고 불변 발송 입력을 저장한다. 0은 발송 선점 전 초기 상태다.
	 * @throws NullPointerException 필수 식별자 또는 문구가 null인 경우
	 * @throws IllegalArgumentException 발송 차수가 음수인 경우
	 */
	public NotificationDelivery(UUID id, UUID userId, String title, String body, String linkUrl, int attemptCount) {
		this(id, userId, title, body, linkUrl, attemptCount, 0);
	}

	/**
	 * 누적 차수와 수동 복구 기준 차수를 검증하고 보관한다.
	 * @throws NullPointerException 필수 값이 null인 경우
	 * @throws IllegalArgumentException 0 <= attemptBase <= attemptCount를 만족하지 않는 경우
	 */
	public NotificationDelivery(UUID id, UUID userId, String title, String body, String linkUrl, int attemptCount, int attemptBase) {
		this.id = Objects.requireNonNull(id, "id");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.title = Objects.requireNonNull(title, "title");
		this.body = Objects.requireNonNull(body, "body");
		this.linkUrl = linkUrl;
		if (attemptCount < 0 || attemptBase < 0 || attemptBase > attemptCount) {
			throw new IllegalArgumentException("0 <= attemptBase <= attemptCount is required");
		}
		this.attemptCount = attemptCount;
		this.attemptBase = attemptBase;
	}

	/** 현재 수동 복구 구간에서 최초 시도를 포함한 횟수다. */
	public int getCycleAttemptCount() {
		return attemptCount - attemptBase;
	}

}
