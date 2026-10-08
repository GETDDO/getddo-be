package com.getddo.core.event.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.event.domain.EventRegistration;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventTimeRange;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.event.domain.RegisteredEvent;
import com.getddo.core.event.exception.EventErrorCode;
import com.getddo.core.event.exception.EventException;
import com.getddo.core.event.repository.EventActorRepository;
import com.getddo.core.event.repository.EventRepository;

/** 이벤트와 경품을 함께 검증하고 등록한다. */
@Service
@RequiredArgsConstructor
public class EventRegistrationService {
	private static final int MAX_DESCRIPTION_BYTES = 65_535;

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
		registration = normalizePeriod(registration);
		validate(registration);
		Instant now = timeProvider.now();
		EventStatus initialStatus = now.isBefore(registration.getStartsAt()) ? EventStatus.SCHEDULED
				: now.isBefore(registration.getEndsAt()) ? EventStatus.OPEN : EventStatus.CLOSED;
		return eventRepository.create(registration, initialStatus);
	}

	private void validate(EventRegistration registration) {
		if (blankOrTooLong(registration.getTitle(), 200) || registration.getDescription() == null
				|| registration.getDescription().isBlank() || descriptionTooLong(registration.getDescription())
				|| tooLong(registration.getImageKey(), 500)) {
			throw new EventException(EventErrorCode.INVALID_DETAILS);
		}
		if (!EventTimeRange.contains(registration.getStartsAt()) || !EventTimeRange.contains(registration.getEndsAt())
				|| !registration.getEndsAt().isAfter(registration.getStartsAt())) {
			throw new EventException(EventErrorCode.INVALID_PERIOD);
		}
		if (registration.getEventType() == null || registration.getMembershipRule() == null
				|| !validTicketConfiguration(registration)) {
			throw new EventException(EventErrorCode.INVALID_CONFIGURATION);
		}
		if (registration.getPrizes() == null || registration.getPrizes().isEmpty()) {
			throw new EventException(EventErrorCode.INVALID_PRIZES);
		}
		Set<Integer> ranks = new HashSet<>();
		for (EventRegistration.Prize prize : registration.getPrizes()) {
			if (prize == null || prize.getRank() < 1 || prize.getWinnerCount() < 1
					|| blankOrTooLong(prize.getName(), 200) || tooLong(prize.getImageKey(), 500)
					|| descriptionTooLong(prize.getDescription())
					|| !ranks.add(prize.getRank())) {
				throw new EventException(EventErrorCode.INVALID_PRIZES);
			}
		}
	}

	/** 저장·기간 검증·초기 상태 판정에 동일한 마이크로초 정밀도의 시각을 사용한다. */
	private EventRegistration normalizePeriod(EventRegistration registration) {
		return new EventRegistration(registration.getActorId(), registration.getTitle(), registration.getDescription(),
				registration.getImageKey(), registration.getEventType(), registration.isWeightingEnabled(),
				registration.getMaxTicketsPerUser(), registration.getMembershipRule(),
				registration.getStartsAt() == null ? null : registration.getStartsAt().truncatedTo(ChronoUnit.MICROS),
				registration.getEndsAt() == null ? null : registration.getEndsAt().truncatedTo(ChronoUnit.MICROS),
				registration.getPrizes());
	}

	private boolean validTicketConfiguration(EventRegistration registration) {
		Integer limit = registration.getMaxTicketsPerUser();
		if (registration.getEventType() == EventType.NO_TICKET) {
			return !registration.isWeightingEnabled() && limit == null;
		}
		if (!registration.isWeightingEnabled()) {
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
