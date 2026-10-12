package com.getddo.api.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import com.getddo.core.notification.domain.AdminNotification;
import com.getddo.core.notification.domain.MockDeliveryStatus;

/** 확정된 관리자 조회 필드만 HTTP 응답으로 변환한다. */
@Builder
public record AdminNotificationResponse(
		UUID id,
		UUID jobId,
		UUID userId,
		UUID eventId,
		String title,
		String body,
		String linkUrl,
		Instant createdAt,
		@JsonProperty("isRead") boolean isRead,
		MockDeliveryStatus mockDeliveryStatus,
		Instant mockSentAt,
		int deliveryAttemptCount,
		Instant nextDeliveryAttemptAt,
		String lastDeliveryError,
		boolean retryable
) {
	public static AdminNotificationResponse from(AdminNotification value) {
		return new AdminNotificationResponse(value.getId(), value.getJobId(), value.getUserId(), value.getEventId(), value.getTitle(), value.getBody(), value.getLinkUrl(), value.getCreatedAt(), value.isRead(), value.getMockDeliveryStatus(), value.getMockSentAt(), value.getDeliveryAttemptCount(), value.getNextDeliveryAttemptAt(), value.getLastDeliveryError(), value.isRetryable());
	}
}
