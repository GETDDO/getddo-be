package com.getddo.api.entry.controller;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.exception.CommonErrorCode;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.entry.dto.response.EntryReceiptResponse;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.entry.domain.EntryHistoryQuery;
import com.getddo.core.entry.domain.EntryStatus;
import com.getddo.core.entry.service.EntryHistoryService;
import com.getddo.core.user.domain.User;

/**
 * 내 응모 이력 조회 API다. 이벤트별(E05)과 전체(E06) 조회가 접수 건과 거절 건을 함께 돌려준다.
 *
 * <p>공통 MVC에서 헤더와 DB 정보를 대조한 사용자의 이력만 조회하며, 멤버십 조건이 없어 멤버십 헤더는 선택이다.</p>
 */
@RestController
@RequestMapping("/api/v1")
public class EntryHistoryController {

	private final EntryHistoryService historyService;

	public EntryHistoryController(EntryHistoryService historyService) {
		this.historyService = historyService;
	}

	/**
	 * 한 이벤트에 대한 내 응모 이력을 최신순으로 조회한다(E05).
	 *
	 * @param user 공통 MVC에서 확인한 등록 사용자
	 * @param eventId 조회할 이벤트. 없으면 404 {@code ENTRY-004}
	 * @param page 1부터 시작하는 페이지 번호
	 * @param size 페이지 크기(1~100)
	 */
	@GetMapping("/events/{eventId}/entries/me")
	public ResponseEnvelope<PageResult<EntryReceiptResponse>> findMyEntriesOfEvent(
			@CurrentUser(membershipRequired = false) User user,
			@PathVariable UUID eventId,
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ResponseEnvelope.success(historyService
				.findMyEntriesOfEvent(user.getId(), eventId, new PageQuery(page, size))
				.map(EntryReceiptResponse::from));
	}

	/**
	 * 내 모든 응모 이력을 조건에 맞게 최신순으로 조회한다(E06).
	 *
	 * @param user 공통 MVC에서 확인한 등록 사용자
	 * @param page 1부터 시작하는 페이지 번호
	 * @param size 페이지 크기(1~100)
	 * @param eventId 이벤트 필터. 생략하면 전체
	 * @param status 처리 결과 필터. 생략하면 접수와 거절 모두
	 * @param from 시작 시각(포함). 생략하면 제한 없음
	 * @param to 끝 시각(제외). 생략하면 제한 없음
	 */
	@GetMapping("/users/me/entries")
	public ResponseEnvelope<PageResult<EntryReceiptResponse>> findMyEntries(
			@CurrentUser(membershipRequired = false) User user,
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(required = false) UUID eventId,
			@RequestParam(required = false) EntryStatus status,
			@Parameter(description = "시작 시각(포함). 시간대가 필요하다. UTC는 Z로 쓰고, 오프셋의 +는 %2B로 인코딩한다.",
					example = "2026-09-01T00:00:00Z")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
			@Parameter(description = "끝 시각(제외). 시간대가 필요하다. UTC는 Z로 쓰고, 오프셋의 +는 %2B로 인코딩한다.",
					example = "2026-10-01T00:00:00Z")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
		if (from != null && to != null && !from.toInstant().isBefore(to.toInstant())) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT);
		}
		EntryHistoryQuery query = new EntryHistoryQuery(eventId, status, from == null ? null : from.toInstant(),
				to == null ? null : to.toInstant());
		return ResponseEnvelope.success(historyService.findMyEntries(user.getId(), query, new PageQuery(page, size))
				.map(EntryReceiptResponse::from));
	}
}
