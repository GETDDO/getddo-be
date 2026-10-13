package com.getddo.core.notification.service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.notification.domain.EventNotificationChange;
import com.getddo.core.notification.domain.NotificationJobRequest;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.exception.NotificationProcessingErrorCode;
import com.getddo.core.notification.exception.NotificationProcessingException;
import com.getddo.core.notification.repository.EventChangeNotificationRepository;
import com.getddo.core.notification.repository.NotificationJobRepository;

/** 일정 변경·취소에 따른 시작 작업 중지와 별도 안내 등록을 함께 확정한다. */
@Service
@RequiredArgsConstructor
public class EventChangeNotificationService {
	private static final DateTimeFormatter DISPLAY_TIME = new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd HH:mm:ss")
			.appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true).toFormatter()
			.withZone(ZoneId.of("Asia/Seoul"));
	private final EventChangeNotificationRepository repository;
	private final NotificationJobRepository jobRepository;
	private final NotificationJobService jobs;

	/**
	 * 이벤트 담당자는 이벤트 저장·취소·반환과 같은 트랜잭션에서 호출한다.
	 * 같은 변경 ID의 재처리는 최초 수신자와 발생 시각을 유지하며, 다른 입력이면 충돌로 거절한다.
	 * 대상이 없어도 빈 작업을 기록해 동일 발생 건의 처리 결과를 보존한다.
	 */
	@Transactional
	public UUID register(EventNotificationChange change) {
		if (change == null) {
			throw new NotificationProcessingException(NotificationProcessingErrorCode.INVALID_JOB);
		}
		repository.lockEvent(change.getEventId());
		UUID existingId = repository.findChangeJobId(change.getOccurrenceKey()).orElse(null);
		if (existingId != null) {
			NotificationJobRequest existing = jobRepository.findRequestForComparison(existingId);
			if (!existing.equals(request(change, existing.getRecipientIds()))) {
				throw new NotificationProcessingException(NotificationProcessingErrorCode.OCCURRENCE_CONFLICT);
			}
			return existingId;
		}
		if (!repository.matchesSavedChange(change)) {
			throw new NotificationProcessingException(NotificationProcessingErrorCode.INVALID_JOB);
		}
		repository.stopStartJobs(change.getEventId(), change.getOccurrenceKey());
		UUID id = jobs.register(request(change, repository.findStartRecipientIds(change.getEventId())));
		repository.linkSourceJob(id, change.getEventId());
		return id;
	}

	/**
	 * 이벤트 논리 삭제를 flush한 같은 트랜잭션에서 호출한다.
	 * 기존 알림·작업 이력은 유지하고 남은 시작 알림 생성만 중지한다. 삭제 안내는 생성하지 않는다.
	 */
	@Transactional
	public void stopForDeletedEvent(UUID eventId) {
		if (eventId == null) {
			throw new NotificationProcessingException(NotificationProcessingErrorCode.INVALID_JOB);
		}
		repository.lockEvent(eventId);
		if (!repository.isDeletedEvent(eventId)) {
			throw new NotificationProcessingException(NotificationProcessingErrorCode.INVALID_JOB);
		}
		repository.stopStartJobs(eventId, "event-deleted:" + eventId);
	}

	private NotificationJobRequest request(EventNotificationChange change, List<UUID> recipients) {
		boolean scheduleChanged = change.getType() == NotificationType.EVENT_SCHEDULE_CHANGED;
		String body = "\"" + change.getEventTitle() + "\" 이벤트가 취소되었습니다.";
		if (scheduleChanged) {
			body = "\"" + change.getEventTitle() + "\" 이벤트 시작 시각이 "
					+ DISPLAY_TIME.format(change.getPreviousStartsAt()) + "에서 "
					+ DISPLAY_TIME.format(change.getStartsAt()) + "으로 변경되었습니다. (한국 시간)";
		}
		return new NotificationJobRequest(change.getOccurrenceKey(), change.getType(), change.getEventId(), null,
				scheduleChanged ? "이벤트 일정 변경 안내" : "이벤트 취소 안내", body,
				"/events/" + change.getEventId(), change.getOccurredAt(), recipients);
	}
}
