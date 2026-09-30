package com.getddo.api.notification.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.getddo.api.common.context.CurrentUserArgumentResolver;
import com.getddo.api.common.exception.GlobalExceptionHandler;
import com.getddo.core.common.pagination.CursorResult;
import com.getddo.core.notification.domain.Notification;
import com.getddo.core.notification.service.NotificationService;
import com.getddo.core.user.domain.Membership;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.user.service.UserService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest {
	private static final UUID USER_ID = UUID.randomUUID();
	private static final User USER = new User(USER_ID, "사용자", UserRole.USER, UserStatus.ACTIVE,
			Membership.VIP, null, null, null, null, null, null);
	private final NotificationService service = mock(NotificationService.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new NotificationController(service))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver(
						new UserService(id -> Optional.of(USER).filter(user -> user.id().equals(id)))))
				.build();
	}

	@Test
	@DisplayName("선택한 사용자의 전체 읽음 결과 건수를 반환한다")
	void readAllUsesSelectedUserAndReturnsUpdatedCount() throws Exception {
		// given
		when(service.markAllRead(USER)).thenReturn(3L);

		// when / then
		mvc.perform(selected(put("/api/v1/notifications/me/read-all")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.updatedCount").value(3));
		verify(service).markAllRead(USER);
	}

	@Test
	@DisplayName("개별 읽음 응답에 알림 ID와 읽음 여부를 반환한다")
	void individualReadReturnsIsReadField() throws Exception {
		// given
		UUID notificationId = UUID.randomUUID();
		when(service.markRead(USER, notificationId)).thenReturn(notificationId);

		// when / then
		mvc.perform(selected(put("/api/v1/notifications/{id}/read", notificationId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(notificationId.toString()))
				.andExpect(jsonPath("$.data.isRead").value(true));
	}

	@Test
	@DisplayName("목록 응답에 공개 필드와 UTC 생성 시각을 반환한다")
	void listReturnsTheSpecifiedFieldsAndUtcInstant() throws Exception {
		// given
		UUID notificationId = UUID.randomUUID();
		Notification notification = new Notification(notificationId, "제목", "내용",
				Instant.parse("2026-09-30T01:00:00Z"), false, null, null);
		when(service.findMine(USER, null, 20, null))
				.thenReturn(new CursorResult<>(List.of(notification), null, 1));

		// when / then
		mvc.perform(selected(get("/api/v1/notifications/me")))
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
				.andExpect(jsonPath("$.code").value("USER-003"));
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
				.header("X-User-ID", USER_ID).header("X-User-Role", "USER")
				.header("X-User-Membership", "gold"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"X-User-Role", "X-User-Membership"})
	@DisplayName("모든 알림 API에서 USER의 필수 헤더를 공통 MVC가 검사한다")
	void allEndpointsRequireUserContextHeaders(String missing) throws Exception {
		for (MockHttpServletRequestBuilder request : endpoints()) {
			selected(request);
			request.with(servletRequest -> {
				servletRequest.removeHeader(missing);
				return servletRequest;
			});
			mvc.perform(request).andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("USER-003"));
		}
		verifyNoInteractions(service);
	}

	@ParameterizedTest
	@CsvSource({
			"X-User-Role, ADMIN, 403, USER-004",
			"X-User-Membership, excellent, 409, USER-006",
			"X-User-ID, 00000000-0000-0000-0000-000000000999, 401, USER-002"
	})
	@DisplayName("모든 알림 API에서 역할·멤버십 불일치와 미등록 사용자를 공통 MVC가 거절한다")
	void allEndpointsRejectInvalidUserContext(String header, String value, int statusCode, String code)
			throws Exception {
		for (MockHttpServletRequestBuilder request : endpoints()) {
			selected(request).with(servletRequest -> {
				servletRequest.removeHeader(header);
				servletRequest.addHeader(header, value);
				return servletRequest;
			});
			mvc.perform(request).andExpect(status().is(statusCode))
					.andExpect(jsonPath("$.code").value(code));
		}
		verifyNoInteractions(service);
	}

	private List<MockHttpServletRequestBuilder> endpoints() {
		return List.of(get("/api/v1/notifications/me"),
				put("/api/v1/notifications/{id}/read", UUID.randomUUID()),
				put("/api/v1/notifications/me/read-all"));
	}

	private MockHttpServletRequestBuilder selected(MockHttpServletRequestBuilder request) {
		return request.header("X-User-ID", USER_ID).header("X-User-Role", "USER")
				.header("X-User-Membership", "vip");
	}
}
