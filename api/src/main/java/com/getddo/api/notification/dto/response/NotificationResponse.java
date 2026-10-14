package com.getddo.api.notification.dto.response;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.getddo.core.notification.domain.Notification;

/** 알림함 목록의 공개 필드다. 내부 사용자·발송 작업 정보는 포함하지 않는다. */
public record NotificationResponse(UUID id, String title, String body, Instant createdAt,
		@JsonProperty("isRead") boolean isRead, UUID eventId, String linkUrl) {
	/**
	 * 서비스의 조회 결과를 담을 불변 응답을 생성한다.
	 *
	 * @param id 알림 ID
	 * @param title 알림 제목
	 * @param body 알림 내용
	 * @param createdAt UTC 생성 시각
	 * @param isRead 읽음 여부
	 * @param eventId 관련 이벤트 ID. 없으면 null
	 * @param linkUrl 관련 화면 링크. 없으면 null
	 * @throws NullPointerException 알림 ID·제목·내용·생성 시각 중 필수 값이 null인 경우
	 */
	public NotificationResponse {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(body, "body");
		Objects.requireNonNull(createdAt, "createdAt");
	}

	/**
	 * 알림 도메인 값을 외부 응답으로 옮긴다.
	 *
	 * @param notification 서비스가 반환한 알림 도메인 값
	 * @return 공개 필드만 담은 응답 DTO
	 */
	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(notification.getId(), notification.getTitle(), notification.getBody(),
				notification.getCreatedAt(), notification.isRead(), notification.getEventId(), notification.getLinkUrl());
	}

}
