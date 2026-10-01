package com.getddo.api.event.controller;

import java.time.Instant;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.event.dto.request.EventWriteRequest;
import com.getddo.api.event.dto.response.AdminEventResponse;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.event.domain.EventQueryFilter;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.event.domain.EventView;
import com.getddo.core.event.service.EventQueryService;
import com.getddo.core.event.service.EventRegistrationService;
import com.getddo.core.user.domain.User;

@RestController
@RequestMapping("/api/v1/admin/events")
public class AdminEventController {
	private final EventRegistrationService registrationService;
	private final EventQueryService queryService;
	private final TimeProvider timeProvider;

	public AdminEventController(EventRegistrationService registrationService, EventQueryService queryService,
			TimeProvider timeProvider) {
		this.registrationService = registrationService;
		this.queryService = queryService;
		this.timeProvider = timeProvider;
	}

	@PostMapping
	public ResponseEntity<ResponseEnvelope<AdminEventResponse>> register(
			@CurrentUser User user,
			@Valid @RequestBody EventWriteRequest request) {
		AdminEventResponse response = AdminEventResponse.from(
				registrationService.register(request.toRegistration(user.id())), timeProvider.now());
		return ResponseEntity.status(HttpStatus.CREATED).body(ResponseEnvelope.success(response));
	}

	/** AE01 관리자 검색. from/to는 양끝 날짜를 포함하는 KST 날짜 범위다. 모집 기간과 겹치면 포함한다. */
	@GetMapping
	public ResponseEnvelope<PageResult<AdminEventResponse>> list(
			@CurrentUser User user,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) EventStatus status,
			@RequestParam(required = false) EventType eventType,
			@Parameter(description = "검색 시작 날짜(KST, 해당 날짜 포함)",
					schema = @Schema(type = "string", format = "date", example = "2026-10-10"))
			@RequestParam(required = false) String from,
			@Parameter(description = "검색 종료 날짜(KST, 해당 날짜 포함)",
					schema = @Schema(type = "string", format = "date", example = "2026-10-20"))
			@RequestParam(required = false) String to) {
		PageResult<EventView> result = queryService.findAdminEvents(user, page, size,
				new EventQueryFilter(status, eventType, null, keyword,
						EventRequestContext.searchFrom(from, timeProvider), EventRequestContext.searchTo(to, timeProvider)));
		Instant now = timeProvider.now();
		return ResponseEnvelope.success(new PageResult<>(
				result.getItems().stream().map(event -> AdminEventResponse.from(event, now)).toList(),
				result.getPage(), result.getSize(), result.getTotalElements()));
	}

	/** AE02 관리자 상세는 실제 운영 상태와 이미지 키를 제공한다. */
	@GetMapping("/{eventId}")
	public ResponseEnvelope<AdminEventResponse> detail(
			@CurrentUser User user,
			@PathVariable String eventId) {
		return ResponseEnvelope.success(AdminEventResponse.from(
				queryService.findAdminEvent(user, EventRequestContext.uuid(eventId)),
				timeProvider.now()));
	}
}
