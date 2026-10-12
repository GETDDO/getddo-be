package com.getddo.api.entry.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.entry.dto.request.EntryRequest;
import com.getddo.api.entry.dto.response.EntryEligibilityResponse;
import com.getddo.api.entry.dto.response.EntryReceiptResponse;
import com.getddo.core.entry.domain.EntryCommand;
import com.getddo.core.entry.domain.EntryReceipt;
import com.getddo.core.entry.service.EntryEligibilityService;
import com.getddo.core.entry.service.EntryService;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;

/**
 * 이벤트 응모(E04)와 응모 자격 사전 조회(E07) API다.
 *
 * <p>공통 MVC에서 헤더와 DB 정보를 대조한 사용자를 기준으로 처리한다. 응모 ID는 서버가 만들지 않고 클라이언트가 요청 전에
 * 정한 UUID를 {@code Idempotency-Key} 헤더로 보낸다.</p>
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}")
public class EntryController {

	private final EntryService entryService;
	private final EntryEligibilityService eligibilityService;

	public EntryController(EntryService entryService, EntryEligibilityService eligibilityService) {
		this.entryService = entryService;
		this.eligibilityService = eligibilityService;
	}

	/**
	 * 이벤트에 응모하고 고른 등급별 응모권을 차감한다.
	 *
	 * <p>같은 {@code Idempotency-Key}로 같은 요청을 다시 보내면 새로 응모하지 않고 이미 접수한 결과를 돌려준다. 같은 키로
	 * 다른 내용의 요청을 보내면 409다.</p>
	 *
	 * @param user 공통 MVC에서 확인한 등록 사용자
	 * @param eventId 응모할 이벤트
	 * @param entryId 응모 ID. 요청 전에 클라이언트가 정한 UUID이며 같은 응모의 재시도는 같은 값을 쓴다
	 * @return 새 응모면 201, 같은 키의 재요청이면 이미 접수한 결과와 200
	 */
	@PostMapping("/entries")
	public ResponseEntity<ResponseEnvelope<EntryReceiptResponse>> enter(
			@CurrentUser User user,
			@PathVariable UUID eventId,
			@Parameter(description = "응모 요청을 구분하는 UUID. 같은 응모의 재시도는 같은 값을 보낸다.")
			@RequestHeader("Idempotency-Key") UUID entryId,
			@Valid @RequestBody EntryRequest request) {
		EntryReceipt receipt = entryService.enter(new EntryCommand(user.id(), user.role() == UserRole.ADMIN,
				user.membership(), eventId, entryId, request.toTickets()));
		return ResponseEntity.status(receipt.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(ResponseEnvelope.success(EntryReceiptResponse.from(receipt)));
	}

	/**
	 * 이벤트에 응모할 수 있는지와 그 사유, 사용·잔여 수량을 조회한다. 응모를 보장하지 않는다.
	 *
	 * @param user 공통 MVC에서 확인한 등록 사용자
	 * @param eventId 조회할 이벤트
	 */
	@GetMapping("/eligibility")
	public ResponseEnvelope<EntryEligibilityResponse> findEligibility(@CurrentUser User user,
			@PathVariable UUID eventId) {
		return ResponseEnvelope.success(EntryEligibilityResponse.from(eligibilityService.check(user.id(),
				user.role() == UserRole.ADMIN, user.membership(), eventId)));
	}
}
