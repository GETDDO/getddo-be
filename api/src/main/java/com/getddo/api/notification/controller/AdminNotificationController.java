package com.getddo.api.notification.controller;

import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.notification.dto.response.AdminNotificationJobResponse;
import com.getddo.api.notification.dto.response.AdminNotificationJobDetailResponse;
import com.getddo.api.notification.dto.response.AdminNotificationResponse;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.notification.domain.MockDeliveryStatus;
import com.getddo.core.notification.domain.NotificationDeliveryQuery;
import com.getddo.core.notification.domain.NotificationJobQuery;
import com.getddo.core.notification.domain.NotificationJobStatus;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.service.NotificationAdminService;
import com.getddo.core.user.domain.User;

/** AN01~AN05 관리자 알림 조회·최종 실패 복구 접수를 제공한다. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminNotificationController {
	private final NotificationAdminService service;
	private final TimeProvider time;

	@GetMapping("/notification-jobs")
	public ResponseEnvelope<PageResult<AdminNotificationJobResponse>> jobs(
			@CurrentUser(membershipRequired = false) User user,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) NotificationJobStatus status,
			@RequestParam(required = false) NotificationType notificationType,
			@RequestParam(required = false) UUID eventId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
		var result = service.findJobs(user, new NotificationJobQuery(page, size, status, notificationType, eventId, from, to));
		var now = time.now();
		return ResponseEnvelope.success(result.map(value -> AdminNotificationJobResponse.from(value, now)));
	}

	@GetMapping("/notification-jobs/{jobId}")
	public ResponseEnvelope<AdminNotificationJobDetailResponse> job(@CurrentUser(membershipRequired = false) User user,
			@PathVariable UUID jobId) {
		return ResponseEnvelope.success(AdminNotificationJobDetailResponse.from(service.findJob(user, jobId), time.now()));
	}

	@PostMapping("/notification-jobs/{jobId}/retry")
	public ResponseEntity<ResponseEnvelope<AdminNotificationJobResponse>> retryJob(
			@CurrentUser(membershipRequired = false) User user, @PathVariable UUID jobId) {
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseEnvelope.success(
				AdminNotificationJobResponse.from(service.retryJob(user, jobId), time.now())));
	}

	@GetMapping("/notifications")
	public ResponseEnvelope<PageResult<AdminNotificationResponse>> notifications(
			@CurrentUser(membershipRequired = false) User user,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) UUID userId, @RequestParam(required = false) UUID eventId,
			@RequestParam(required = false) UUID jobId, @RequestParam(required = false) MockDeliveryStatus mockDeliveryStatus,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
		return ResponseEnvelope.success(service.findDeliveries(user,
				new NotificationDeliveryQuery(page, size, userId, eventId, jobId, mockDeliveryStatus, from, to))
				.map(AdminNotificationResponse::from));
	}

	@PostMapping("/notifications/{notificationId}/delivery-retries")
	public ResponseEntity<ResponseEnvelope<AdminNotificationResponse>> retryDelivery(
			@CurrentUser(membershipRequired = false) User user, @PathVariable UUID notificationId) {
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseEnvelope.success(
				AdminNotificationResponse.from(service.retryDelivery(user, notificationId))));
	}
}
