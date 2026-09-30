package com.getddo.api.event.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.exception.CommonErrorCode;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.event.dto.request.EventWriteRequest;
import com.getddo.api.event.dto.response.AdminEventResponse;
import com.getddo.api.event.mapper.EventResponseMapper;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.event.exception.EventErrorCode;
import com.getddo.core.event.service.EventRegistrationService;

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
			@RequestHeader(value = "X-User-ID", required = false) String userId,
			@Valid @RequestBody EventWriteRequest request) {
		UUID actorId = parseUserId(userId);
		AdminEventResponse response = responseMapper.toResponse(
				registrationService.register(request.toRegistration(actorId)), timeProvider.now());
		return ResponseEntity.status(HttpStatus.CREATED).body(ResponseEnvelope.success(response));
	}

	private UUID parseUserId(String value) {
		if (value == null || value.isBlank()) {
			throw new BusinessException(EventErrorCode.USER_CONTEXT_REQUIRED);
		}
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.INVALID_FORMAT);
		}
	}
}
