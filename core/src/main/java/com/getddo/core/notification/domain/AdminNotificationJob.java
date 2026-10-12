package com.getddo.core.notification.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;

import com.getddo.core.event.domain.EventStatus;

/** 관리자 알림 조회의 불변 저장소 결과다. HTTP DTO와 분리한다. */
@Getter
public final class AdminNotificationJob {
	private final UUID id;
	private final UUID eventId;
	private final UUID publicationId;
	private final NotificationType notificationType;
	private final NotificationJobStatus status;
	private final int attemptCount;
	private final int attemptBase;
	private final Instant scheduledAt;
	private final Instant nextAttemptAt;
	private final Instant createdAt;
	private final Instant completedAt;
	private final String generationStoppedBy;
	private final String lastError;
	private final UUID sourceJobId;
	private final UUID lastProcessedUserId;
	private final Instant leaseUntil;
	private final EventStatus eventStatus;
	private final Instant eventStartsAt;
	private final boolean eventDeleted;

	/** 저장소가 읽은 값과 복구 가능 여부 계산에 필요한 근거를 보관한다. */
	public AdminNotificationJob(UUID id,
			UUID eventId,
			UUID publicationId,
			NotificationType notificationType,
			NotificationJobStatus status,
			int attemptCount,
			int attemptBase,
			Instant scheduledAt,
			Instant nextAttemptAt,
			Instant createdAt,
			Instant completedAt,
			String generationStoppedBy,
			String lastError,
			UUID sourceJobId,
			UUID lastProcessedUserId,
			Instant leaseUntil,
			EventStatus eventStatus,
			Instant eventStartsAt,
			boolean eventDeleted) {
		this.id = id;
		this.eventId = eventId;
		this.publicationId = publicationId;
		this.notificationType = notificationType;
		this.status = status;
		this.attemptCount = attemptCount;
		this.attemptBase = attemptBase;
		this.scheduledAt = scheduledAt;
		this.nextAttemptAt = nextAttemptAt;
		this.createdAt = createdAt;
		this.completedAt = completedAt;
		this.generationStoppedBy = generationStoppedBy;
		this.lastError = lastError;
		this.sourceJobId = sourceJobId;
		this.lastProcessedUserId = lastProcessedUserId;
		this.leaseUntil = leaseUntil;
		this.eventStatus = eventStatus;
		this.eventStartsAt = eventStartsAt;
		this.eventDeleted = eventDeleted;
	}

	/** 최종 실패이며 미중지·실행 가능한 시작 안내만 관리자 생성 복구를 허용한다. */
	public boolean isRetryable(Instant now) {
		return status == NotificationJobStatus.FAILED && nextAttemptAt == null && leaseUntil == null
				&& generationStoppedBy == null && attemptCount < Integer.MAX_VALUE
				&& (notificationType != NotificationType.EVENT_START
						|| (eventId != null && !eventDeleted && eventStatus == EventStatus.SCHEDULED
						&& eventStartsAt != null && eventStartsAt.isAfter(now)));
	}
}
