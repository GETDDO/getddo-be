package com.getddo.api.attendance.controller;

import java.time.YearMonth;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.attendance.dto.response.AttendanceMonthResponse;
import com.getddo.api.attendance.dto.response.AttendanceReceiptResponse;
import com.getddo.api.attendance.dto.response.AttendanceTodayResponse;
import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.core.attendance.domain.AttendanceReceipt;
import com.getddo.core.attendance.service.AttendanceQueryService;
import com.getddo.core.attendance.service.AttendanceService;
import com.getddo.core.user.domain.User;

/**
 * AT01~AT03 출석 API다.
 *
 * <p>공통 MVC에서 헤더와 DB 정보를 대조한 사용자를 서버 KST 업무일로 출석 처리하거나 출석 현황을 조회한다.
 * 출석 처리(AT02)는 날짜와 보상량을 입력받지 않는다.</p>
 */
@RestController
@RequestMapping("/api/v1/attendances")
public class AttendanceController {
	private final AttendanceService service;
	private final AttendanceQueryService queryService;

	public AttendanceController(AttendanceService service, AttendanceQueryService queryService) {
		this.service = service;
		this.queryService = queryService;
	}

	/**
	 * 오늘 출석하고 일일·연속 출석 보상을 함께 확정한다.
	 *
	 * @param user 공통 MVC에서 확인한 등록 사용자
	 * @return 새 출석이면 201, 같은 날 재요청이면 이미 확정된 결과와 200
	 */
	@PostMapping
	public ResponseEntity<ResponseEnvelope<AttendanceReceiptResponse>> attend(
			@CurrentUser(membershipRequired = false) User user) {
		AttendanceReceipt receipt = service.attend(user.id());
		return ResponseEntity.status(receipt.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(ResponseEnvelope.success(AttendanceReceiptResponse.from(receipt)));
	}

	/**
	 * 오늘(KST 업무일) 출석 현황을 조회한다.
	 *
	 * @param user 공통 MVC에서 확인한 등록 사용자
	 * @return 출석 여부, 오늘 기준 연속 일수, 일일 보상 수량, 이번 달 단계 현황, 다음 초기화 시각
	 */
	@GetMapping("/today")
	public ResponseEnvelope<AttendanceTodayResponse> findToday(@CurrentUser(membershipRequired = false) User user) {
		return ResponseEnvelope.success(AttendanceTodayResponse.from(queryService.getToday(user.id())));
	}

	/**
	 * 한 달의 출석 날짜와 그 달에 적용된 단계 현황을 조회한다.
	 *
	 * <p>기록이 없는 달이나 미래의 달도 오류가 아니라 빈 결과다.</p>
	 *
	 * @param user 공통 MVC에서 확인한 등록 사용자
	 * @param month 조회할 KST 월, {@code YYYY-MM} 형식
	 * @return 출석한 날짜 목록과 단계 현황
	 */
	@GetMapping
	public ResponseEnvelope<AttendanceMonthResponse> findMonth(
			@CurrentUser(membershipRequired = false) User user,
			@Parameter(description = "조회할 KST 월. YYYY-MM 형식만 허용한다.", example = "2026-10")
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
		return ResponseEnvelope.success(AttendanceMonthResponse.from(queryService.getMonth(user.id(), month)));
	}
}
