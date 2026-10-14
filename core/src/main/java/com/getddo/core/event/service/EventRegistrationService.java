package com.getddo.core.event.service;

import java.time.Instant;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.event.domain.EventRegistration;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.RegisteredEvent;
import com.getddo.core.event.exception.EventErrorCode;
import com.getddo.core.event.exception.EventException;
import com.getddo.core.event.repository.EventActorRepository;
import com.getddo.core.event.repository.EventRepository;

/** 이벤트와 경품을 함께 검증하고 등록한다. */
@Service
@RequiredArgsConstructor
public class EventRegistrationService {

	private final EventRepository eventRepository;
	private final EventActorRepository actorRepository;
	private final TimeProvider timeProvider;

	@Transactional
	public RegisteredEvent register(EventRegistration registration) {
		if (registration == null || registration.getActorId() == null) {
			throw new EventException(EventErrorCode.USER_CONTEXT_REQUIRED);
		}
		EventActorRepository.Actor actor = actorRepository.findById(registration.getActorId())
				.orElseThrow(() -> new EventException(EventErrorCode.USER_CONTEXT_REQUIRED));
		if (!actor.isActive() || !actor.isAdmin()) {
			throw new EventException(EventErrorCode.ADMIN_REQUIRED);
		}
		registration = EventWriteValidator.normalizeAndValidate(registration);
		if (registration.getPrizes().stream().anyMatch(prize -> prize.getId() != null)) {
			throw new EventException(EventErrorCode.INVALID_PRIZES);
		}
		Instant now = timeProvider.now();
		EventStatus initialStatus = now.isBefore(registration.getStartsAt()) ? EventStatus.SCHEDULED
				: now.isBefore(registration.getEndsAt()) ? EventStatus.OPEN : EventStatus.CLOSED;
		return eventRepository.create(registration, initialStatus);
	}
}
