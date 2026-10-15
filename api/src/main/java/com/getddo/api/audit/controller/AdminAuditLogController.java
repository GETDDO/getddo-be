package com.getddo.api.audit.controller;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.audit.dto.request.AuditLogQueryRequest;
import com.getddo.api.audit.dto.response.AuditLogDetailResponse;
import com.getddo.api.audit.dto.response.AuditLogSummaryResponse;
import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.exception.CommonErrorCode;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.core.audit.service.AuditLogQueryService;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.user.domain.User;

/** 감사 목록·상세의 HTTP 입력과 응답 형식을 담당한다. */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {
	private final AuditLogQueryService service;

	@GetMapping
	public ResponseEnvelope<PageResult<AuditLogSummaryResponse>> list(
			@CurrentUser(membershipRequired = false) User actor,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
			@ModelAttribute @ParameterObject AuditLogQueryRequest request, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) throw new BusinessException(CommonErrorCode.INVALID_FORMAT);
		return ResponseEnvelope.success(service.findAll(actor, request.toDomain(page, size)).map(AuditLogSummaryResponse::from));
	}

	@GetMapping("/{auditLogId}")
	public ResponseEnvelope<AuditLogDetailResponse> detail(@CurrentUser(membershipRequired = false) User actor,
			@PathVariable UUID auditLogId) {
		return ResponseEnvelope.success(AuditLogDetailResponse.from(service.findById(actor, auditLogId)));
	}
}
