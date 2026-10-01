package com.getddo.api.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.getddo.core.notification.domain.Notification;

/** 알림함 목록의 공개 필드다. 내부 사용자·발송 작업 정보는 포함하지 않는다. */
public final class NotificationResponse {
	private final UUID id;
	private final String title;
	private final String body;
	private final Instant createdAt;
	private final boolean isRead;
	private final UUID eventId;
	private final String linkUrl;

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
	 */
	public NotificationResponse(UUID id, String title, String body, Instant createdAt,
			boolean isRead, UUID eventId, String linkUrl) {
		this.id = id;
		this.title = title;
		this.body = body;
		this.createdAt = createdAt;
		this.isRead = isRead;
		this.eventId = eventId;
		this.linkUrl = linkUrl;
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

	public UUID getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	/** boolean getter의 기본 이름인 read 대신 기존 공개 필드명 isRead를 유지한다. */
	@JsonProperty("isRead")
	public boolean isRead() {
		return isRead;
	}

	public UUID getEventId() {
		return eventId;
	}

	public String getLinkUrl() {
		return linkUrl;
	}
}
