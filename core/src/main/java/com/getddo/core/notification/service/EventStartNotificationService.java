package com.getddo.core.notification.service;

import java.time.Duration;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.notification.domain.EventStartNotification;
import com.getddo.core.notification.domain.NotificationJobRequest;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.repository.EventStartNotificationRepository;

/** 이벤트의 저장된 시작 시각을 예약 기준으로 삼아 시작 알림 작업을 한 번 등록한다. */
@Service
@RequiredArgsConstructor
public class EventStartNotificationService {
	private static final Duration NOTICE_BEFORE = Duration.ofMinutes(10);
	private final EventStartNotificationRepository repository;
	private final NotificationJobService jobs;
	private final TimeProvider timeProvider;

	/**
	 * 시작 알림 대상 조회와 작업 등록을 한 트랜잭션으로 확정한다.
	 *
	 * <p>10분 전을 놓쳤어도 아직 시작 전이면 등록하며, 시작 시각부터는 새 작업을 등록하지 않는다.
	 * 같은 이벤트를 다른 서버가 동시에 선택하지 못하도록 조회에서 이벤트 행을 잠근다.
	 * 최초 등록 때 수신자를 고정하므로 부분 생성 실패 후 재시도에도 대상이 달라지지 않는다.</p>
	 *
	 * @return 작업을 등록하면 true, 처리할 이벤트가 없으면 false
	 */
	@Transactional
	public boolean registerNextDueEvent() {
		Instant now = timeProvider.now();
		EventStartNotification event = repository.findNextDueEvent(now, now.plus(NOTICE_BEFORE)).orElse(null);
		if (event == null) {
			return false;
		}
		jobs.register(new NotificationJobRequest("event-start:" + event.getId(),
				NotificationType.EVENT_START, event.getId(), null, "이벤트 시작 안내",
				"\"" + event.getTitle() + "\" 이벤트가 곧 시작됩니다.", "/events/" + event.getId(),
				event.getStartsAt().minus(NOTICE_BEFORE), repository.findRecipientIds(event.getMembershipRule())));
		return true;
	}
}
