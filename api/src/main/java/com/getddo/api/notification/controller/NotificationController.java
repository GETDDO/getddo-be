package com.getddo.api.notification.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.notification.dto.response.NotificationResponse;
import com.getddo.api.notification.dto.response.NotificationReadResponse;
import com.getddo.api.notification.dto.response.NotificationReadAllResponse;
import com.getddo.core.common.pagination.CursorResult;
import com.getddo.core.notification.domain.Notification;
import com.getddo.core.notification.service.NotificationService;
import com.getddo.core.user.domain.User;

/**
 * N01~N03 사용자 알림 API다.
 *
 * <p>공통 MVC에서 헤더와 DB 정보를 대조한 사용자를 받는다.
 * 알림 소유권 검사는 서비스에 위임한다.</p>
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
	private final NotificationService service;

	public NotificationController(NotificationService service) {
		this.service = service;
	}

	/**
	 * 본인 알림을 커서로 조회하며 조회 자체로 읽음 처리하지 않는다.
	 *
	 * @param user 공통 MVC에서 확인한 등록 사용자
	 * @param cursor 이전 응답의 nextCursor. 첫 조회에서는 생략
	 * @param size 조회할 최대 건수. 기본 20, 허용 범위 1~100
	 * @param isRead 읽음 상태 필터. 생략하면 읽음·미읽음 모두 조회
	 * @return 알림 목록과 다음 커서, 조회 조건에 맞는 전체 건수
	 */
	@GetMapping("/me")
	public ResponseEnvelope<CursorResult<NotificationResponse>> findMine(
			@CurrentUser User user,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) Boolean isRead) {
		CursorResult<Notification> result = service.findMine(
				user, cursor, size, isRead);
		return ResponseEnvelope.success(new CursorResult<>(
				result.getItems().stream().map(NotificationResponse::from).toList(),
				result.getNextCursor(), result.getTotalElements()));
	}

	/**
	 * 본인 알림 한 건을 읽음 처리한다. 이미 읽은 알림의 재요청도 성공한다.
	 *
	 * @param user 공통 MVC에서 확인한 등록 사용자
	 * @param notificationId 읽음 처리할 알림 ID
	 * @return 알림 ID와 읽음 상태가 true인 응답
	 */
	@PutMapping("/{notificationId}/read")
	public ResponseEnvelope<NotificationReadResponse> markRead(
			@CurrentUser User user,
			@PathVariable UUID notificationId) {
		UUID id = service.markRead(user, notificationId);
		return ResponseEnvelope.success(new NotificationReadResponse(id, true));
	}

	/**
	 * 본인의 미읽음 알림 전체를 읽음 처리하고 실제 변경 건수를 반환한다.
	 *
	 * @param user 공통 MVC에서 확인한 등록 사용자
	 * @return 이미 읽은 알림을 제외한 실제 변경 건수
	 */
	@PutMapping("/me/read-all")
	public ResponseEnvelope<NotificationReadAllResponse> markAllRead(
			@CurrentUser User user) {
		return ResponseEnvelope.success(new NotificationReadAllResponse(
				service.markAllRead(user)));
	}
}
