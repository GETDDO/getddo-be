package com.getddo.api.notification.dto.response;

import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 개별 알림의 읽음 처리 결과다. 읽음 시각은 제공하지 않는다. */
public final class NotificationReadResponse {
	private final UUID id;
	private final boolean isRead;

	/**
	 * 읽음 처리 결과를 불변 응답으로 보관한다.
	 *
	 * @param id 읽음 처리한 본인 알림 ID
	 * @param isRead 읽음 처리가 성공한 경우 true
	 * @throws NullPointerException 필수 알림 ID가 null인 경우
	 */
	public NotificationReadResponse(UUID id, boolean isRead) {
		this.id = Objects.requireNonNull(id, "id");
		this.isRead = isRead;
	}

	public UUID getId() {
		return id;
	}

	/** 클래스 전환 뒤에도 응답 필드명을 isRead로 유지한다. */
	@JsonProperty("isRead")
	public boolean isRead() {
		return isRead;
	}
}
