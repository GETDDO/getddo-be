package com.getddo.core.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 사용자 알림함에 표시하는 불변 알림 조회 모델이다.
 *
 * <p>생성 시각은 UTC이며 읽음 시각은 관리하지 않는다.
 * 관련 이벤트가 없으면 이벤트 ID와 링크는 null이다.
 * 생성자에서 필수 식별자를 검증하고 getter와 값 비교 메서드를 제공한다.</p>
 */
@Getter
@EqualsAndHashCode
public final class Notification {
	/** 알림 식별자. */
	private final UUID id;
	/** 사용자에게 표시할 제목. */
	private final String title;
	/** 사용자에게 표시할 내용. */
	private final String body;
	/** 알림이 생성된 UTC 순간. */
	private final Instant createdAt;
	/** 본인의 읽음 여부. 모의 발송 결과와 별개다. */
	private final boolean isRead;
	/** 관련 이벤트 ID. 없으면 null이다. */
	private final UUID eventId;
	/** 이동할 링크. 없으면 null이다. */
	private final String linkUrl;

	/** 불변 조회 값을 저장하며 필수 알림 ID가 null이면 거절한다. */
	public Notification(UUID id, String title, String body, Instant createdAt,
			boolean isRead, UUID eventId, String linkUrl) {
		this.id = Objects.requireNonNull(id, "id");
		this.title = title;
		this.body = body;
		this.createdAt = createdAt;
		this.isRead = isRead;
		this.eventId = eventId;
		this.linkUrl = linkUrl;
	}
}
