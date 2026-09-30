package com.getddo.core.notification.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 사용자 알림함에 표시하는 알림이다.
 *
 * <p>{@code createdAt}은 UTC 시각이며, 읽음 시각은 관리하지 않는다.
 * 관련 이벤트가 없는 알림은 {@code eventId}와 {@code linkUrl}이 null일 수 있다.</p>
 *
 * @param id 알림 식별자
 * @param title 사용자에게 표시할 제목
 * @param body 사용자에게 표시할 내용
 * @param createdAt 알림 생성 순간
 * @param isRead 본인의 읽음 처리 여부. 모의 발송 결과와 별개
 * @param eventId 관련 이벤트 ID. 없으면 null
 * @param linkUrl 알림에서 이동할 링크. 없으면 null
 */
public record Notification(UUID id, String title, String body, Instant createdAt,
		boolean isRead, UUID eventId, String linkUrl) {
}
