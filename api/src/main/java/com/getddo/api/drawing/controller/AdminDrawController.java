package com.getddo.api.drawing.controller;

import java.util.UUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.drawing.dto.response.*;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.drawing.service.DrawInspectionService;
import com.getddo.core.user.domain.User;

/** AD01·02·03·04·08. 실행·재개·취소·생성·공개 요청은 제공하지 않는다. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminDrawController {
	private final DrawInspectionService service;

	/** 관리자에게 이벤트별 실행 목록의 한 페이지를 제공한다. */
	@GetMapping("/events/{eventId}/draws")
	public ResponseEnvelope<PageResult<DrawRunResponse>> runs(@CurrentUser(membershipRequired = false) User user, @PathVariable UUID eventId,
			@RequestParam(defaultValue = "1") @Min(1) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ResponseEnvelope.success(service.runs(user, eventId, new PageQuery(page, size)).map(DrawRunResponse::from));
	}
	/** 저장된 실행·결과와 실패·취소 요약을 조회한다. */
	@GetMapping("/draws/{drawId}")
	public ResponseEnvelope<DrawRunDetailResponse> detail(@CurrentUser(membershipRequired = false) User user, @PathVariable UUID drawId) {
		return ResponseEnvelope.success(DrawRunDetailResponse.from(service.detail(user, drawId)));
	}
	/** 요청한 실행에 고정된 후보만 페이징한다. */
	@GetMapping("/draws/{drawId}/participants")
	public ResponseEnvelope<PageResult<DrawCandidateResponse>> candidates(@CurrentUser(membershipRequired = false) User user, @PathVariable UUID drawId,
			@RequestParam(defaultValue = "1") @Min(1) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ResponseEnvelope.success(service.candidates(user, drawId, new PageQuery(page, size)).map(DrawCandidateResponse::from));
	}
	/** 실행의 전체 실패 이력을 안전한 분류 문구로 변환해 반환한다. */
	@GetMapping("/draws/{drawId}/failures")
	public ResponseEnvelope<PageResult<DrawFailureResponse>> failures(
			@CurrentUser(membershipRequired = false) User user, @PathVariable UUID drawId,
			@RequestParam(defaultValue = "1") @Min(1) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ResponseEnvelope.success(service.failures(user, drawId, new PageQuery(page, size)).map(DrawFailureResponse::from));
	}

	/** 실제 실행·재개 처리자의 감사 이력을 API DTO로 반환한다. */
	@GetMapping("/draws/{drawId}/execution-audits")
	public ResponseEnvelope<PageResult<DrawExecutionAuditResponse>> executionAudits(
			@CurrentUser(membershipRequired = false) User user, @PathVariable UUID drawId,
			@RequestParam(defaultValue = "1") @Min(1) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ResponseEnvelope.success(service.executionAudits(user, drawId, new PageQuery(page, size)).map(DrawExecutionAuditResponse::from));
	}

	/** 저장 근거의 정합성을 검증하고 검증 이력을 남긴다. */
	@PostMapping("/draws/{drawId}/checks")
	public ResponseEntity<ResponseEnvelope<DrawVerificationResponse>> verify(@CurrentUser(membershipRequired = false) User user, @PathVariable UUID drawId) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ResponseEnvelope.success(
				DrawVerificationResponse.from(service.verify(user, drawId))));
	}
	/** 기존 검증 이력의 한 페이지를 반환한다. */
	@GetMapping("/draws/{drawId}/checks")
	public ResponseEnvelope<PageResult<DrawVerificationResponse>> history(@CurrentUser(membershipRequired = false) User user, @PathVariable UUID drawId,
			@RequestParam(defaultValue = "1") @Min(1) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ResponseEnvelope.success(service.history(user, drawId, new PageQuery(page, size)).map(DrawVerificationResponse::from));
	}
}
