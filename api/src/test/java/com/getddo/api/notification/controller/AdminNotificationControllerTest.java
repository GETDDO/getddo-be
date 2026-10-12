package com.getddo.api.notification.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.getddo.api.common.context.CurrentUserArgumentResolver;
import com.getddo.api.common.exception.GlobalExceptionHandler;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.notification.domain.AdminNotification;
import com.getddo.core.notification.domain.MockDeliveryStatus;
import com.getddo.core.notification.service.NotificationAdminService;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.user.service.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminNotificationControllerTest {
	private static final UUID ADMIN_ID = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-10-12T01:00:00Z");
	private final NotificationAdminService service = mock(NotificationAdminService.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		User user = new User(ADMIN_ID, "관리자", UserRole.ADMIN, UserStatus.ACTIVE, null, null, null, null, null, null, null);
		mvc = MockMvcBuilders.standaloneSetup(new AdminNotificationController(service,
				new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC))))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver(new UserService(id -> Optional.of(user))))
				.build();
	}

	@Test
	@DisplayName("관리자 작업 목록은 공통 페이지 응답을 반환한다")
	void returnsPageEnvelope() throws Exception {
		// given
		when(service.findJobs(any(), any())).thenReturn(new PageResult<>(List.of(), 1, 20, 0));
		// when / then
		mvc.perform(get("/api/v1/admin/notification-jobs").header("X-User-ID", ADMIN_ID).header("X-User-Role", "ADMIN"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isArray())
				.andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.size").value(20));
	}

	@Test
	@DisplayName("잘못된 조회 기간·상태·크기는 400으로 반환한다")
	void rejectsMalformedQueries() throws Exception {
		// given / when / then
		for (String query : List.of("size=101", "status=WRONG", "from=2026-10-12T02:00:00Z&to=2026-10-12T01:00:00Z")) {
			mvc.perform(get("/api/v1/admin/notification-jobs?" + query)
					.header("X-User-ID", ADMIN_ID).header("X-User-Role", "ADMIN"))
					.andExpect(status().isBadRequest());
		}
	}

	@Test
	@DisplayName("발송 복구 접수는 202이며 isRead JSON 필드명을 유지한다")
	void acceptsDeliveryRetry() throws Exception {
		// given
		UUID id = UUID.randomUUID();
		when(service.retryDelivery(any(), eq(id))).thenReturn(new AdminNotification(id, UUID.randomUUID(), UUID.randomUUID(),
				null, "제목", "내용", null, NOW, true, MockDeliveryStatus.PENDING, null, 4, 4, NOW, null));
		// when / then
		mvc.perform(post("/api/v1/admin/notifications/" + id + "/delivery-retries")
				.header("X-User-ID", ADMIN_ID).header("X-User-Role", "ADMIN"))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$.data.isRead").value(true))
				.andExpect(jsonPath("$.data.deliveryAttemptCount").value(4));
	}
}
