package com.getddo.api.ticket.controller;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.ticket.dto.response.AdminTicketHistoryResponse;
import com.getddo.api.ticket.dto.response.TicketConsistencyResponse;
import com.getddo.api.ticket.dto.response.TicketGrantResponse;
import com.getddo.api.ticket.dto.response.TicketInconsistencyResponse;
import com.getddo.api.ticket.dto.response.TicketRefundResponse;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.ticket.domain.AdminTicketHistoryFilter;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketGrantFilter;
import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.core.ticket.domain.TicketRefundFilter;
import com.getddo.core.ticket.service.AdminTicketQueryService;
import com.getddo.core.user.domain.User;

/** AO03·AO04·AO05·AO08·AO09 관리자 응모권 지급·처리·반환 이력과 정합성 조회. 읽기 전용이며 지급·회수·수량 조정 경로는 제공하지 않는다. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminTicketController {

	private final AdminTicketQueryService service;

	/**
	 * 응모권 지급 이력을 지급 시각 최신순으로 조회한다. 기간은 {@code [from, to)}이며 시각은 {@code Z}나 오프셋을 포함해야 한다.
	 *
	 * @param user 공통 MVC에서 확인한 요청 사용자. 활성 관리자여야 한다
	 * @param keyword 사용자 이름 부분 일치(대소문자 무시)
	 * @param missionId 미션 지급만 좁힌다
	 */
	@GetMapping("/ticket-grants")
	public ResponseEnvelope<PageResult<TicketGrantResponse>> grants(
			@CurrentUser(membershipRequired = false) User user,
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(required = false) UUID userId,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) UUID missionId,
			@RequestParam(required = false) GrantSourceType sourceType,
			@RequestParam(required = false) TicketGrade grade,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
		TicketGrantFilter filter = TicketGrantFilter.of(userId, keyword, missionId, sourceType, grade,
				from == null ? null : from.toInstant(), to == null ? null : to.toInstant());
		return ResponseEnvelope.success(
				service.getGrants(user, filter, new PageQuery(page, size)).map(TicketGrantResponse::from));
	}

	/**
	 * AO05 응모권 처리 이력을 처리 시각 최신순으로 조회한다. 기간은 {@code [from, to)}이며 시각은 {@code Z}나 오프셋을 포함해야 한다.
	 *
	 * @param user 공통 MVC에서 확인한 요청 사용자. 활성 관리자여야 한다
	 * @param eventId 사용·반환이 이 이벤트의 응모에 속하는 이력만 좁힌다
	 * @param missionId 이 미션 지급으로 만들어진 응모권의 이력만 좁힌다
	 */
	@GetMapping("/ticket-histories")
	public ResponseEnvelope<PageResult<AdminTicketHistoryResponse>> histories(
			@CurrentUser(membershipRequired = false) User user,
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(required = false) UUID userId,
			@RequestParam(required = false) UUID eventId,
			@RequestParam(required = false) UUID missionId,
			@RequestParam(required = false) TicketOperationType operationType,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
		AdminTicketHistoryFilter filter = AdminTicketHistoryFilter.of(userId, eventId, missionId, operationType,
				from == null ? null : from.toInstant(), to == null ? null : to.toInstant());
		return ResponseEnvelope.success(service.getHistories(user, filter, new PageQuery(page, size))
				.map(AdminTicketHistoryResponse::from));
	}

	/**
	 * AO08 응모권 반환 이력을 반환 시각 최신순으로 조회한다. 기간은 {@code [from, to)}이며 시각은 {@code Z}나 오프셋을 포함해야 한다.
	 *
	 * @param user 공통 MVC에서 확인한 요청 사용자. 활성 관리자여야 한다
	 * @param eventId 이 이벤트의 취소로 반환된 이력만 좁힌다
	 */
	@GetMapping("/ticket-refunds")
	public ResponseEnvelope<PageResult<TicketRefundResponse>> refunds(
			@CurrentUser(membershipRequired = false) User user,
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(required = false) UUID eventId,
			@RequestParam(required = false) UUID userId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
		TicketRefundFilter filter = TicketRefundFilter.of(eventId, userId, from == null ? null : from.toInstant(),
				to == null ? null : to.toInstant());
		return ResponseEnvelope.success(
				service.getRefunds(user, filter, new PageQuery(page, size)).map(TicketRefundResponse::from));
	}

	/**
	 * AO04 한 사용자의 응모권 현재 상태와 마지막 처리 이력의 정합성을 확인한다. 없는 사용자는 404다.
	 *
	 * @param user 공통 MVC에서 확인한 요청 사용자. 활성 관리자여야 한다
	 * @param userId 확인할 사용자 ID
	 */
	@GetMapping("/users/{userId}/tickets/consistency")
	public ResponseEnvelope<TicketConsistencyResponse> consistency(
			@CurrentUser(membershipRequired = false) User user, @PathVariable UUID userId) {
		return ResponseEnvelope.success(TicketConsistencyResponse.from(service.getConsistency(user, userId)));
	}

	/**
	 * AO09 전체 사용자의 어긋난 응모권을 요청 시점에 계산해 조회한다.
	 *
	 * @param user 공통 MVC에서 확인한 요청 사용자. 활성 관리자여야 한다
	 * @param userId 사용자로 좁히려면 사용자 ID
	 */
	@GetMapping("/tickets/inconsistencies")
	public ResponseEnvelope<PageResult<TicketInconsistencyResponse>> inconsistencies(
			@CurrentUser(membershipRequired = false) User user,
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(required = false) UUID userId) {
		return ResponseEnvelope.success(TicketInconsistencyResponse.from(
				service.getInconsistencies(user, userId, new PageQuery(page, size))));
	}
}
