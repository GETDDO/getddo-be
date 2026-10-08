package com.getddo.api.event.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.event.dto.request.EventWriteRequest;
import com.getddo.api.event.dto.response.AdminEventResponse;
import com.getddo.api.event.mapper.EventResponseMapper;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.event.service.EventRegistrationService;
import com.getddo.core.user.domain.User;

@RestController
@RequestMapping("/api/v1/admin/events")
public class AdminEventController {
	private final EventRegistrationService registrationService;
	private final TimeProvider timeProvider;
	private final EventResponseMapper responseMapper;

	public AdminEventController(EventRegistrationService registrationService, TimeProvider timeProvider,
			EventResponseMapper responseMapper) {
		this.registrationService = registrationService;
		this.timeProvider = timeProvider;
		this.responseMapper = responseMapper;
	}

	@PostMapping
	public ResponseEntity<ResponseEnvelope<AdminEventResponse>> register(
			@CurrentUser User user,
			@Valid @RequestBody EventWriteRequest request) {
		AdminEventResponse response = responseMapper.toResponse(
				registrationService.register(user.id(), request.toRegistration()), timeProvider.now());
		return ResponseEntity.status(HttpStatus.CREATED).body(ResponseEnvelope.success(response));
	}
}
