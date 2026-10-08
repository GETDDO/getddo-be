package com.getddo.core.event.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.event.domain.EventRegistration;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.event.domain.RegisteredEvent;
import com.getddo.core.event.exception.EventErrorCode;
import com.getddo.core.event.repository.EventActorRepository;
import com.getddo.core.event.repository.EventRepository;

/** 이벤트와 경품을 함께 검증하고 등록한다. */
@Service
public class EventRegistrationService {
	private static final int MAX_DESCRIPTION_BYTES = 65_535;

	private final EventRepository eventRepository;
	private final EventActorRepository actorRepository;
	private final TimeProvider timeProvider;

	public EventRegistrationService(EventRepository eventRepository, EventActorRepository actorRepository,
			TimeProvider timeProvider) {
		this.eventRepository = eventRepository;
		this.actorRepository = actorRepository;
		this.timeProvider = timeProvider;
	}

	@Transactional
	public RegisteredEvent register(UUID adminId, EventRegistration registration) {
		if (adminId == null || registration == null) {
			throw new BusinessException(EventErrorCode.USER_CONTEXT_REQUIRED);
		}
		EventActorRepository.Actor actor = actorRepository.findById(adminId)
				.orElseThrow(() -> new BusinessException(EventErrorCode.USER_CONTEXT_REQUIRED));
		if (!actor.active() || !actor.admin()) {
			throw new BusinessException(EventErrorCode.ADMIN_REQUIRED);
		}
		validate(registration);
		Instant now = timeProvider.now();
		EventStatus initialStatus = now.isBefore(registration.startsAt()) ? EventStatus.SCHEDULED
				: now.isBefore(registration.endsAt()) ? EventStatus.OPEN : EventStatus.CLOSED;
		return eventRepository.create(copyPrizes(registration), initialStatus);
	}

	private EventRegistration copyPrizes(EventRegistration registration) {
		return new EventRegistration(registration.title(), registration.description(),
				registration.imageKey(), registration.eventType(), registration.weightingEnabled(),
				registration.maxTicketsPerUser(), registration.membershipRule(), registration.startsAt(),
				registration.endsAt(), List.copyOf(registration.prizes()));
	}

	private void validate(EventRegistration registration) {
		if (blankOrTooLong(registration.title(), 200) || registration.description() == null
				|| registration.description().isBlank() || descriptionTooLong(registration.description())
				|| tooLong(registration.imageKey(), 500)) {
			throw new BusinessException(EventErrorCode.INVALID_DETAILS);
		}
		if (registration.startsAt() == null || registration.endsAt() == null
				|| !registration.endsAt().isAfter(registration.startsAt())) {
			throw new BusinessException(EventErrorCode.INVALID_PERIOD);
		}
		if (registration.eventType() == null || registration.membershipRule() == null
				|| !validTicketConfiguration(registration)) {
			throw new BusinessException(EventErrorCode.INVALID_CONFIGURATION);
		}
		if (registration.prizes() == null || registration.prizes().isEmpty()) {
			throw new BusinessException(EventErrorCode.INVALID_PRIZES);
		}
		Set<Integer> ranks = new HashSet<>();
		for (EventRegistration.Prize prize : registration.prizes()) {
			if (prize == null || prize.rank() < 1 || prize.winnerCount() < 1
					|| blankOrTooLong(prize.name(), 200) || tooLong(prize.imageKey(), 500)
					|| descriptionTooLong(prize.description())
					|| !ranks.add(prize.rank())) {
				throw new BusinessException(EventErrorCode.INVALID_PRIZES);
			}
		}
	}

	private boolean validTicketConfiguration(EventRegistration registration) {
		Integer limit = registration.maxTicketsPerUser();
		if (registration.eventType() == EventType.NO_TICKET) {
			return !registration.weightingEnabled() && limit == null;
		}
		if (!registration.weightingEnabled()) {
			return Integer.valueOf(1).equals(limit);
		}
		return limit == null || Integer.valueOf(5).equals(limit);
	}

	private boolean blankOrTooLong(String value, int maximum) {
		return value == null || value.isBlank() || value.length() > maximum;
	}

	private boolean tooLong(String value, int maximum) {
		return value != null && value.length() > maximum;
	}

	private boolean descriptionTooLong(String value) {
		return value != null && value.getBytes(StandardCharsets.UTF_8).length > MAX_DESCRIPTION_BYTES;
	}
}
