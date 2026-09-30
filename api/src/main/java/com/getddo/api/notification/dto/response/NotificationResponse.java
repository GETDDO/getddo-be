package com.getddo.api.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.getddo.core.notification.domain.Notification;

/**
 * 알림함 목록의 공개 필드다. 내부 사용자·발송 작업 정보는 포함하지 않는다.
 *
 * @param id 알림 ID
 * @param title 알림 제목
 * @param body 알림 내용
 * @param createdAt UTC ISO-8601로 직렬화할 생성 시각
 * @param isRead 읽음 여부
 * @param eventId 관련 이벤트 ID. 없으면 null
 * @param linkUrl 관련 화면 링크. 없으면 null
 */
public record NotificationResponse(UUID id, String title, String body, Instant createdAt,
		boolean isRead, UUID eventId, String linkUrl) {
	/**
	 * 알림 도메인 값을 외부 응답으로 옮긴다.
	 *
	 * @param notification 서비스가 반환한 알림 도메인 값
	 * @return 공개 필드만 담은 응답 DTO
	 */
	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(notification.id(), notification.title(), notification.body(),
				notification.createdAt(), notification.isRead(), notification.eventId(), notification.linkUrl());
	}
}
