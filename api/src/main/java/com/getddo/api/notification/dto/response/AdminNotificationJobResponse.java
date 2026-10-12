package com.getddo.api.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;

import com.getddo.core.notification.domain.AdminNotificationJob;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.domain.NotificationJobStatus;

/** 확정된 관리자 조회 필드만 HTTP 응답으로 변환한다. */
@Builder
public record AdminNotificationJobResponse(
		UUID id,
		UUID eventId,
		UUID publicationId,
		NotificationType notificationType,
		NotificationJobStatus status,
		int attemptCount,
		Instant scheduledAt,
		Instant nextAttemptAt,
		Instant createdAt,
		Instant completedAt,
		String generationStoppedBy,
		boolean retryable
) {
	public static AdminNotificationJobResponse from(AdminNotificationJob value, Instant now) {
		return new AdminNotificationJobResponse(value.getId(), value.getEventId(), value.getPublicationId(), value.getNotificationType(), value.getStatus(), value.getAttemptCount(), value.getScheduledAt(), value.getNextAttemptAt(), value.getCreatedAt(), value.getCompletedAt(), value.getGenerationStoppedBy(), value.isRetryable(now));
	}
}
