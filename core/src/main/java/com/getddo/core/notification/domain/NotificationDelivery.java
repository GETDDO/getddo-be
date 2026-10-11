package com.getddo.core.notification.domain;

import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** 이미 생성된 알림의 모의 발송 입력이다. 읽음 상태를 포함하거나 변경하지 않는다. */
@Getter
public final class NotificationDelivery {
	private final UUID id;
	private final UUID userId;
	private final String title;
	private final String body;
	private final String linkUrl;
	private final int attemptCount;

	/**
	 * 필수 식별자·문구를 검증하고 불변 발송 입력을 저장한다. 0은 발송 선점 전 초기 상태다.
	 * @throws NullPointerException 필수 식별자 또는 문구가 null인 경우
	 * @throws IllegalArgumentException 발송 차수가 음수인 경우
	 */
	public NotificationDelivery(UUID id, UUID userId, String title, String body, String linkUrl, int attemptCount) {
		this.id = Objects.requireNonNull(id, "id");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.title = Objects.requireNonNull(title, "title");
		this.body = Objects.requireNonNull(body, "body");
		this.linkUrl = linkUrl;
		if (attemptCount < 0) {
			throw new IllegalArgumentException("attemptCount must be non-negative");
		}
		this.attemptCount = attemptCount;
	}

	/** 모든 저장 값을 비교해 같은 입력 또는 조회 결과인지 판정한다. */
	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof NotificationDelivery that)) {
			return false;
		}
		return Objects.equals(id, that.id)
				&& Objects.equals(userId, that.userId)
				&& Objects.equals(title, that.title)
				&& Objects.equals(body, that.body)
				&& Objects.equals(linkUrl, that.linkUrl)
				&& attemptCount == that.attemptCount;
	}

	/** 값이 같은 객체는 동일한 해시값을 반환한다. */
	@Override
	public int hashCode() {
		return Objects.hash(id, userId, title, body, linkUrl, attemptCount);
	}

}
