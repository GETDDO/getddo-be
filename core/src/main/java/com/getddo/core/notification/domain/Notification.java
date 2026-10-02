package com.getddo.core.notification.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.NonNull;
import lombok.Value;

/**
 * 사용자 알림함에 표시하는 불변 알림 조회 모델이다.
 *
 * <p>생성 시각은 UTC이며 읽음 시각은 관리하지 않는다.
 * 관련 이벤트가 없으면 이벤트 ID와 링크는 null이다.
 * Lombok이 생성자·getter·값 비교 메서드를 생성한다.</p>
 */
@Value
public class Notification {
	/** 알림 식별자. */
	@NonNull
	UUID id;
	/** 사용자에게 표시할 제목. */
	String title;
	/** 사용자에게 표시할 내용. */
	String body;
	/** 알림이 생성된 UTC 순간. */
	Instant createdAt;
	/** 본인의 읽음 여부. 모의 발송 결과와 별개다. */
	boolean isRead;
	/** 관련 이벤트 ID. 없으면 null이다. */
	UUID eventId;
	/** 이동할 링크. 없으면 null이다. */
	String linkUrl;
}
