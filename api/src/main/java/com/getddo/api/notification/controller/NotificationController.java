package com.getddo.api.notification.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.exception.CommonErrorCode;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.notification.dto.response.NotificationResponse;
import com.getddo.api.notification.dto.response.NotificationReadResponse;
import com.getddo.api.notification.dto.response.NotificationReadAllResponse;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.CursorResult;
import com.getddo.core.notification.domain.Notification;
import com.getddo.core.notification.domain.NotificationErrorCode;
import com.getddo.core.notification.service.NotificationService;

/**
 * N01~N03 사용자 알림 API다.
 *
 * <p>시연용 {@code X-User-ID}를 직접 해석한다. 선택적 멤버십은 형식만 확인하고
 * DB 값 대조와 알림 소유권 검사는 서비스에 위임한다.</p>
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
	 * @param selectedUserId 시연용으로 선택한 등록 사용자 UUID 문자열
	 * @param selectedMembership DB 값과 대조할 선택적 멤버십. 생략하면 null
	 * @param cursor 이전 응답의 nextCursor. 첫 조회에서는 생략
	 * @param size 조회할 최대 건수. 기본 20, 허용 범위 1~100
	 * @param isRead 읽음 상태 필터. 생략하면 읽음·미읽음 모두 조회
	 * @return 알림 목록과 다음 커서, 조회 조건에 맞는 전체 건수
	 */
	@GetMapping("/me")
	public ResponseEnvelope<CursorResult<NotificationResponse>> findMine(
			@RequestHeader(value = "X-User-ID", required = false) String selectedUserId,
			@RequestHeader(value = "X-User-Membership", required = false) String selectedMembership,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) Boolean isRead) {
		CursorResult<Notification> result = service.findMine(
				userId(selectedUserId), membership(selectedMembership), cursor, size, isRead);
		return ResponseEnvelope.success(new CursorResult<>(
				result.items().stream().map(NotificationResponse::from).toList(),
				result.nextCursor(), result.totalElements()));
	}

	/**
	 * 본인 알림 한 건을 읽음 처리한다. 이미 읽은 알림의 재요청도 성공한다.
	 *
	 * @param selectedUserId 시연용으로 선택한 등록 사용자 UUID 문자열
	 * @param selectedMembership DB 값과 대조할 선택적 멤버십
	 * @param notificationId 읽음 처리할 알림 ID
	 * @return 알림 ID와 읽음 상태가 true인 응답
	 */
	@PutMapping("/{notificationId}/read")
	public ResponseEnvelope<NotificationReadResponse> markRead(
			@RequestHeader(value = "X-User-ID", required = false) String selectedUserId,
			@RequestHeader(value = "X-User-Membership", required = false) String selectedMembership,
			@PathVariable UUID notificationId) {
		UUID id = service.markRead(userId(selectedUserId), membership(selectedMembership), notificationId);
		return ResponseEnvelope.success(new NotificationReadResponse(id, true));
	}

	/**
	 * 본인의 미읽음 알림 전체를 읽음 처리하고 실제 변경 건수를 반환한다.
	 *
	 * @param selectedUserId 시연용으로 선택한 등록 사용자 UUID 문자열
	 * @param selectedMembership DB 값과 대조할 선택적 멤버십
	 * @return 이미 읽은 알림을 제외한 실제 변경 건수
	 */
	@PutMapping("/me/read-all")
	public ResponseEnvelope<NotificationReadAllResponse> markAllRead(
			@RequestHeader(value = "X-User-ID", required = false) String selectedUserId,
			@RequestHeader(value = "X-User-Membership", required = false) String selectedMembership) {
		return ResponseEnvelope.success(new NotificationReadAllResponse(
				service.markAllRead(userId(selectedUserId), membership(selectedMembership))));
	}

	/** 필수 사용자 헤더를 UUID로 해석한다. 없는 ID의 판정은 서비스가 수행한다. */
	private UUID userId(String selectedUserId) {
		if (selectedUserId == null) {
			throw new BusinessException(NotificationErrorCode.USER_CONTEXT_REQUIRED);
		}
		try {
			UUID id = UUID.fromString(selectedUserId);
			if (!id.toString().equalsIgnoreCase(selectedUserId)) {
				throw new IllegalArgumentException();
			}
			return id;
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.INVALID_FORMAT);
		}
	}

	/** 선택적 멤버십 헤더의 허용 값만 통과시킨다. DB와의 일치 여부는 서비스가 확인한다. */
	private String membership(String selectedMembership) {
		if (selectedMembership == null) {
			return null;
		}
		return switch (selectedMembership) {
			case "excellent", "vip", "vvip" -> selectedMembership;
			default -> throw new BusinessException(CommonErrorCode.INVALID_FORMAT);
		};
	}

}
