package com.getddo.api.event.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.event.dto.response.EventDetailResponse;
import com.getddo.api.event.dto.response.EventSummaryResponse;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.event.domain.EventQueryFilter;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.event.domain.EventView;
import com.getddo.core.event.domain.MembershipRule;
import com.getddo.core.event.service.EventQueryService;
import com.getddo.core.user.domain.User;

/** E01·E02 사용자 이벤트 목록·상세. 관리자도 이 조회 경로를 사용할 수 있다. */
@RestController
@RequestMapping("/api/v1/events")
public class EventController {
	private final EventQueryService queryService;
	private final TimeProvider timeProvider;

	public EventController(EventQueryService queryService, TimeProvider timeProvider) {
		this.queryService = queryService;
		this.timeProvider = timeProvider;
	}

	@GetMapping
	public ResponseEnvelope<PageResult<EventSummaryResponse>> list(
			@CurrentUser User user,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) EventStatus status,
			@RequestParam(required = false) EventType eventType,
			@RequestParam(required = false) MembershipRule membershipRule) {
		PageResult<EventView> result = queryService.findUserEvents(user, page, size,
				new EventQueryFilter(status, eventType, membershipRule, null, null, null));
		Instant now = timeProvider.now();
		return ResponseEnvelope.success(new PageResult<>(
				result.getItems().stream().map(event -> EventSummaryResponse.from(event, now)).toList(),
				result.getPage(), result.getSize(), result.getTotalElements()));
	}

	@GetMapping("/{eventId}")
	public ResponseEnvelope<EventDetailResponse> detail(
			@CurrentUser User user,
			@PathVariable String eventId) {
		return ResponseEnvelope.success(EventDetailResponse.from(
				queryService.findUserEvent(user,
						EventRequestContext.uuid(eventId)), timeProvider.now()));
	}
}
