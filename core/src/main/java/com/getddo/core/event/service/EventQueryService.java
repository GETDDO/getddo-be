package com.getddo.core.event.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.event.domain.EventQueryFilter;
import com.getddo.core.event.domain.EventTimeRange;
import com.getddo.core.event.domain.EventView;
import com.getddo.core.event.exception.EventErrorCode;
import com.getddo.core.event.exception.EventException;
import com.getddo.core.event.repository.EventQueryRepository;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;

import lombok.RequiredArgsConstructor;

/** 사용자·관리자 조회 권한을 확인하고 동일 트랜잭션에서 목록과 전체 건수를 읽는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventQueryService {
	private final EventQueryRepository events;

	public PageResult<EventView> findUserEvents(User user, int page, int size, EventQueryFilter filter) {
		validateActor(user, false);
		return events.findAll(validateQuery(page, size, filter), new PageQuery(page, size), true);
	}

	public EventView findUserEvent(User user, UUID eventId) {
		validateActor(user, false);
		return findEvent(eventId);
	}

	public PageResult<EventView> findAdminEvents(User user, int page, int size, EventQueryFilter filter) {
		validateActor(user, true);
		return events.findAll(validateQuery(page, size, filter), new PageQuery(page, size), false);
	}

	public EventView findAdminEvent(User user, UUID eventId) {
		validateActor(user, true);
		return findEvent(eventId);
	}

	private EventView findEvent(UUID eventId) {
		if (eventId == null) {
			throw new EventException(EventErrorCode.INVALID_QUERY);
		}
		return events.findById(eventId)
				.orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
	}

	private void validateActor(User user, boolean adminOnly) {
		if (user == null) {
			throw new EventException(EventErrorCode.USER_CONTEXT_REQUIRED);
		}
		if (user.status() != UserStatus.ACTIVE || (adminOnly && user.role() != UserRole.ADMIN)) {
			throw new EventException(EventErrorCode.ACCESS_DENIED);
		}
	}

	private EventQueryFilter validateQuery(int page, int size, EventQueryFilter filter) {
		if (page < 1 || size < 1 || size > 100 || filter == null
				|| (filter.getFrom() != null && !EventTimeRange.contains(filter.getFrom()))
				|| (filter.getTo() != null && !EventTimeRange.contains(filter.getTo()))
				|| (filter.getFrom() != null && filter.getTo() != null
						&& !filter.getFrom().isBefore(filter.getTo()))) {
			throw new EventException(EventErrorCode.INVALID_QUERY);
		}
		return filter;
	}
}
