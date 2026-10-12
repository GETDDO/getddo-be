package com.getddo.core.notification.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;

/** 관리자 알림 조회의 불변 저장소 결과다. HTTP DTO와 분리한다. */
@Getter
public final class AdminNotification {
	private final UUID id;
	private final UUID jobId;
	private final UUID userId;
	private final UUID eventId;
	private final String title;
	private final String body;
	private final String linkUrl;
	private final Instant createdAt;
	private final boolean isRead;
	private final MockDeliveryStatus mockDeliveryStatus;
	private final Instant mockSentAt;
	private final int deliveryAttemptCount;
	private final int deliveryAttemptBase;
	private final Instant nextDeliveryAttemptAt;
	private final String lastDeliveryError;

	/** 저장소가 읽은 값과 복구 가능 여부 계산에 필요한 근거를 보관한다. */
	public AdminNotification(UUID id,
			UUID jobId,
			UUID userId,
			UUID eventId,
			String title,
			String body,
			String linkUrl,
			Instant createdAt,
			boolean isRead,
			MockDeliveryStatus mockDeliveryStatus,
			Instant mockSentAt,
			int deliveryAttemptCount,
			int deliveryAttemptBase,
			Instant nextDeliveryAttemptAt,
			String lastDeliveryError) {
		this.id = id;
		this.jobId = jobId;
		this.userId = userId;
		this.eventId = eventId;
		this.title = title;
		this.body = body;
		this.linkUrl = linkUrl;
		this.createdAt = createdAt;
		this.isRead = isRead;
		this.mockDeliveryStatus = mockDeliveryStatus;
		this.mockSentAt = mockSentAt;
		this.deliveryAttemptCount = deliveryAttemptCount;
		this.deliveryAttemptBase = deliveryAttemptBase;
		this.nextDeliveryAttemptAt = nextDeliveryAttemptAt;
		this.lastDeliveryError = lastDeliveryError;
	}

	/** 이미 생성된 알림의 최종 모의 발송 실패만 복구한다. 읽음 상태와는 별개다. */
	public boolean isRetryable() {
		return mockDeliveryStatus == MockDeliveryStatus.FAILED && nextDeliveryAttemptAt == null
				&& deliveryAttemptCount < Integer.MAX_VALUE;
	}
}
