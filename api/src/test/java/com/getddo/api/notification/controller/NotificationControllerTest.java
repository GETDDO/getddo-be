package com.getddo.api.notification.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.getddo.api.common.exception.GlobalExceptionHandler;
import com.getddo.core.common.pagination.CursorResult;
import com.getddo.core.notification.domain.Notification;
import com.getddo.core.notification.service.NotificationService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest {
	private final NotificationService service = mock(NotificationService.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new NotificationController(service))
				.setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@Test
	@DisplayName("선택한 사용자의 전체 읽음 결과 건수를 반환한다")
	void readAllUsesSelectedUserAndReturnsUpdatedCount() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		when(service.markAllRead(userId, null)).thenReturn(3L);

		// when / then
		mvc.perform(put("/api/v1/notifications/me/read-all").header("X-User-ID", userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.updatedCount").value(3));
		verify(service).markAllRead(userId, null);
	}

	@Test
	@DisplayName("개별 읽음 응답에 알림 ID와 읽음 여부를 반환한다")
	void individualReadReturnsIsReadField() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		when(service.markRead(userId, null, notificationId)).thenReturn(notificationId);

		// when / then
		mvc.perform(put("/api/v1/notifications/{id}/read", notificationId)
				.header("X-User-ID", userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(notificationId.toString()))
				.andExpect(jsonPath("$.data.isRead").value(true));
	}

	@Test
	@DisplayName("목록 응답에 공개 필드와 UTC 생성 시각을 반환한다")
	void listReturnsTheSpecifiedFieldsAndUtcInstant() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		Notification notification = new Notification(notificationId, "제목", "내용",
				Instant.parse("2026-09-30T01:00:00Z"), false, null, null);
		when(service.findMine(userId, null, null, 20, null))
				.thenReturn(new CursorResult<>(List.of(notification), null, 1));

		// when / then
		mvc.perform(get("/api/v1/notifications/me").header("X-User-ID", userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(notificationId.toString()))
				.andExpect(jsonPath("$.data.items[0].isRead").value(false))
				.andExpect(jsonPath("$.data.items[0].createdAt").value("2026-09-30T01:00:00Z"))
				.andExpect(jsonPath("$.data.totalElements").value(1));
	}

	@Test
	@DisplayName("사용자 헤더가 없으면 401로 거절한다")
	void missingUserSelectionIsRejected() throws Exception {
		// given: 사용자 선택 헤더가 없는 요청이다.

		// when / then
		mvc.perform(put("/api/v1/notifications/me/read-all"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER_CONTEXT_REQUIRED"));
	}

	@Test
	@DisplayName("UUID나 멤버십 형식이 잘못되면 400으로 거절한다")
	void malformedUserIdAndMembershipAreBadRequests() throws Exception {
		// given: 잘못된 UUID 또는 멤버십 헤더를 전달한다.

		// when / then
		mvc.perform(put("/api/v1/notifications/me/read-all")
				.header("X-User-ID", "not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
		mvc.perform(put("/api/v1/notifications/me/read-all")
				.header("X-User-ID", UUID.randomUUID())
				.header("X-User-Membership", "gold"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
	}
}
