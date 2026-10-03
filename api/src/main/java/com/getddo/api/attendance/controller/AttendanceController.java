package com.getddo.api.attendance.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.attendance.dto.response.AttendanceReceiptResponse;
import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.core.attendance.domain.AttendanceReceipt;
import com.getddo.core.attendance.service.AttendanceService;
import com.getddo.core.user.domain.User;

/**
 * AT02 출석 API다.
 *
 * <p>공통 MVC에서 헤더와 DB 정보를 대조한 사용자를 서버 KST 업무일로 출석 처리한다. 날짜와 보상량은 입력받지 않는다.</p>
 */
@RestController
@RequestMapping("/api/v1/attendances")
public class AttendanceController {
	private final AttendanceService service;

	public AttendanceController(AttendanceService service) {
		this.service = service;
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
}
