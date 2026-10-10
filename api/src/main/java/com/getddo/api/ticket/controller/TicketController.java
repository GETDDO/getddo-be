package com.getddo.api.ticket.controller;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.ticket.dto.response.MyTicketsResponse;
import com.getddo.api.ticket.dto.response.TicketHistoryResponse;
import com.getddo.core.common.pagination.CursorResult;
import com.getddo.core.ticket.domain.TicketHistoryFilter;
import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.core.ticket.service.TicketQueryService;
import com.getddo.core.user.domain.User;

/**
 * T01·T02 내 응모권 조회 API다.
 *
 * <p>공통 MVC에서 헤더와 DB 정보를 대조한 사용자의 응모권만 조회한다. 조회 조건 검증은 서비스에 위임한다.</p>
 */
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {
	private final TicketQueryService service;

	public TicketController(TicketQueryService service) {
		this.service = service;
	}

	/**
	 * 조회 시각에 사용할 수 있는 내 응모권을 등급별로 조회한다.
	 *
	 * @param user 공통 MVC에서 확인한 등록 사용자
	 * @return 사용 가능 장수, 등급별 장수, 등급·만료 시각별 묶음, 서버 시각
	 */
	@GetMapping("/me")
	public ResponseEnvelope<MyTicketsResponse> findMyTickets(@CurrentUser(membershipRequired = false) User user) {
		return ResponseEnvelope.success(MyTicketsResponse.from(service.getMyTickets(user.id())));
	}

	/**
	 * 내 응모권 처리 이력을 {@code createdAt DESC, id DESC} 순서의 커서로 조회한다.
	 *
	 * <p>기간은 {@code [from, to)}로 적용한다. 시각은 {@code Z}나 오프셋을 포함해야 하며 시간대 없는 값은 거절한다.
	 * 쿼리 문자열에서 오프셋의 {@code +}는 공백으로 해석되므로 {@code %2B}로 인코딩하거나 {@code Z}를 쓴다.</p>
	 *
	 * @param user 공통 MVC에서 확인한 등록 사용자
	 * @param cursor 이전 응답의 nextCursor. 첫 조회에서는 생략
	 * @param size 조회할 최대 건수. 기본 20, 허용 범위 1~100
	 * @param operationType 처리 유형 필터. 생략하면 전체
	 * @param from 시작 시각(포함). 생략하면 제한 없음
	 * @param to 끝 시각(제외). 생략하면 제한 없음
	 * @return 이력 목록과 다음 커서, 조회 조건에 맞는 전체 건수
	 */
	@GetMapping("/histories/me")
	public ResponseEnvelope<CursorResult<TicketHistoryResponse>> findMyHistory(
			@CurrentUser(membershipRequired = false) User user,
			@RequestParam(required = false) String cursor,
			@Parameter(description = "조회할 최대 건수. 1~100, 범위를 벗어나면 조회 조건 오류로 응답한다",
					schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) TicketOperationType operationType,
			@Parameter(description = "시작 시각(포함). 시간대가 필요하다. UTC는 Z로 쓰고, 오프셋의 +는 %2B로 인코딩한다.",
					example = "2026-09-01T00:00:00Z")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
			@Parameter(description = "끝 시각(제외). 시간대가 필요하다. UTC는 Z로 쓰고, 오프셋의 +는 %2B로 인코딩한다.",
					example = "2026-10-01T00:00:00Z")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
		TicketHistoryFilter filter = TicketHistoryFilter.of(operationType,
				from == null ? null : from.toInstant(), to == null ? null : to.toInstant());
		return ResponseEnvelope.success(
				service.getMyHistory(user.id(), filter, cursor, size).map(TicketHistoryResponse::from));
	}
}
