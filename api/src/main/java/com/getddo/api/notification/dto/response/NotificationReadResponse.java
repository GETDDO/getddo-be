package com.getddo.api.notification.dto.response;

import java.util.UUID;

/**
 * 개별 알림의 읽음 처리 결과다. 읽음 시각은 제공하지 않는다.
 *
 * @param id 읽음 처리한 본인 알림 ID
 * @param isRead 읽음 처리가 성공한 경우 true
 */
public record NotificationReadResponse(UUID id, boolean isRead) {
}
