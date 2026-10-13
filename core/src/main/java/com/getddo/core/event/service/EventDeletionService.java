package com.getddo.core.event.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.event.domain.RegisteredEvent;
import com.getddo.core.event.exception.EventErrorCode;
import com.getddo.core.event.exception.EventException;
import com.getddo.core.event.repository.EventActorRepository;
import com.getddo.core.event.repository.EventRepository;
import com.getddo.core.notification.service.EventChangeNotificationService;

/** 시작 전이며 응모 이력이 없는 이벤트의 관리자 삭제를 처리한다. */
@Service
@RequiredArgsConstructor
public class EventDeletionService {
	private final EventRepository events;
	private final EventActorRepository actors;
	private final TimeProvider timeProvider;
	private final EventChangeNotificationService notifications;

	/** 이벤트 잠금 이후의 시각·최신 응모 이력으로 삭제 자격을 검사한다. */
	@Transactional
	public void delete(UUID eventId, UUID actorId) {
		requireAdmin(actorId);
		RegisteredEvent current = eventId == null ? null : events.findByIdForUpdate(eventId).orElse(null);
		if (current == null) {
			throw new EventException(EventErrorCode.EVENT_NOT_FOUND);
		}
		Instant now = timeProvider.now();
		if (!now.isBefore(current.getStartsAt()) || events.hasParticipationHistory(eventId)) {
			throw new EventException(EventErrorCode.DELETION_NOT_ALLOWED);
		}
		events.softDelete(eventId, now.truncatedTo(ChronoUnit.MICROS));
		notifications.stopForDeletedEvent(eventId);
	}

	private void requireAdmin(UUID actorId) {
		if (actorId == null) {
			throw new EventException(EventErrorCode.USER_CONTEXT_REQUIRED);
		}
		EventActorRepository.Actor actor = actors.findById(actorId)
				.orElseThrow(() -> new EventException(EventErrorCode.USER_CONTEXT_REQUIRED));
		if (!actor.isActive() || !actor.isAdmin()) {
			throw new EventException(EventErrorCode.ADMIN_REQUIRED);
		}
	}
}
