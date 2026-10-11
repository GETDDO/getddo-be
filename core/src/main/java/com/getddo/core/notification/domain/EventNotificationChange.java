package com.getddo.core.notification.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import lombok.Getter;

import com.getddo.core.notification.exception.NotificationProcessingErrorCode;
import com.getddo.core.notification.exception.NotificationProcessingException;

/** 이벤트 담당자가 저장한 일정 변경·취소 사실이다. 재요청에는 동일 ID와 입력을 전달한다. */
@Getter
public final class EventNotificationChange {
	private final UUID occurrenceId;
	private final UUID eventId;
	private final NotificationType type;
	private final String eventTitle;
	private final Instant previousStartsAt;
	private final Instant startsAt;
	private final Instant occurredAt;

	private EventNotificationChange(UUID occurrenceId, UUID eventId, NotificationType type,
			String eventTitle, Instant previousStartsAt, Instant startsAt, Instant occurredAt) {
		if (occurrenceId == null || eventId == null || eventTitle == null || eventTitle.isBlank()
				|| eventTitle.codePointCount(0, eventTitle.length()) > 200 || occurredAt == null) {
			throw new NotificationProcessingException(NotificationProcessingErrorCode.INVALID_JOB);
		}
		this.occurrenceId = occurrenceId;
		this.eventId = eventId;
		this.type = type;
		this.eventTitle = eventTitle;
		this.previousStartsAt = micros(previousStartsAt);
		this.startsAt = micros(startsAt);
		this.occurredAt = micros(occurredAt);
		if (type == NotificationType.EVENT_SCHEDULE_CHANGED
				&& (this.previousStartsAt == null || this.startsAt == null
				|| !this.previousStartsAt.isAfter(this.occurredAt)
				|| !this.startsAt.isAfter(this.previousStartsAt))) {
			throw new NotificationProcessingException(NotificationProcessingErrorCode.INVALID_JOB);
		}
	}

	/** 시작 전의 실제 일정 연기만 전달한다. 동일 일정이나 앞당기는 변경은 허용하지 않는다. */
	public static EventNotificationChange scheduleChanged(UUID occurrenceId, UUID eventId, String eventTitle,
			Instant previousStartsAt, Instant startsAt, Instant occurredAt) {
		return new EventNotificationChange(occurrenceId, eventId, NotificationType.EVENT_SCHEDULE_CHANGED,
				eventTitle, previousStartsAt, startsAt, occurredAt);
	}

	/** 취소는 시작 이후에도 허용된다. 취소·반환 업무 자체는 호출 담당자가 처리한다. */
	public static EventNotificationChange canceled(UUID occurrenceId, UUID eventId, String eventTitle,
			Instant occurredAt) {
		return new EventNotificationChange(occurrenceId, eventId, NotificationType.EVENT_CANCELED,
				eventTitle, null, null, occurredAt);
	}

	public String getOccurrenceKey() {
		return "event-change:" + occurrenceId;
	}

	private static Instant micros(Instant value) {
		return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
	}
}
