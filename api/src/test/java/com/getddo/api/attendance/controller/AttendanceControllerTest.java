package com.getddo.api.attendance.controller;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.HandlerMethod;

import com.getddo.api.common.config.OpenApiConfig;
import com.getddo.api.common.context.CurrentUserArgumentResolver;
import com.getddo.api.common.exception.GlobalExceptionHandler;
import com.getddo.core.attendance.domain.AttendanceReceipt;
import com.getddo.core.attendance.domain.AttendanceRewardReceipt;
import com.getddo.core.attendance.domain.AttendanceRewardType;
import com.getddo.core.attendance.exception.AttendanceErrorCode;
import com.getddo.core.attendance.exception.AttendanceException;
import com.getddo.core.attendance.service.AttendanceService;
import com.getddo.core.user.domain.Membership;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttendanceControllerTest {
	private static final UUID USER_ID = UUID.randomUUID();
	private static final User USER = new User(USER_ID, "사용자", UserRole.USER, UserStatus.ACTIVE,
			Membership.VIP, null, null, null, null, null, null);
	private static final UUID ATTENDANCE_ID = UUID.randomUUID();
	private static final UUID DAILY_CLAIM = UUID.randomUUID();
	private static final UUID STREAK_CLAIM = UUID.randomUUID();
	/** 2026-09-07 12:00 KST. */
	private static final Instant NOW = Instant.parse("2026-09-07T03:00:00Z");
	private static final Instant EXPIRES_AT = Instant.parse("2026-09-30T15:00:00Z");
	private final AttendanceService service = mock(AttendanceService.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new AttendanceController(service))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver(
						new UserService(id -> Optional.of(USER).filter(user -> user.id().equals(id)))))
				.build();
	}

	@Test
	@DisplayName("새 출석이면 201과 출석·일일·단계 보상 영수증을 반환한다")
	void returnsCreatedForNewAttendance() throws Exception {
		// given
		when(service.attend(USER_ID)).thenReturn(receipt(true));

		// when / then
		mvc.perform(selected(post("/api/v1/attendances")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.attendanceId").value(ATTENDANCE_ID.toString()))
				.andExpect(jsonPath("$.data.attendanceDate").value("2026-09-07"))
				.andExpect(jsonPath("$.data.consecutiveDays").value(7))
				.andExpect(jsonPath("$.data.createdAt").value("2026-09-07T03:00:00Z"))
				.andExpect(jsonPath("$.data.rewards.length()").value(2))
				.andExpect(jsonPath("$.data.rewards[0].claimId").value(DAILY_CLAIM.toString()))
				.andExpect(jsonPath("$.data.rewards[0].ticketCount").value(1))
				.andExpect(jsonPath("$.data.rewards[0].grantedAt").value("2026-09-07T03:00:00Z"))
				.andExpect(jsonPath("$.data.rewards[0].expiresAt").value("2026-09-30T15:00:00Z"))
				.andExpect(jsonPath("$.data.rewards[1].claimId").value(STREAK_CLAIM.toString()))
				.andExpect(jsonPath("$.data.rewards[1].ticketCount").value(1));
	}

	@Test
	@DisplayName("같은 날 재요청이면 200과 이미 확정된 결과를 반환한다")
	void returnsOkForRepeatedAttendance() throws Exception {
		// given
		when(service.attend(USER_ID)).thenReturn(receipt(false));

		// when / then
		mvc.perform(selected(post("/api/v1/attendances")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.attendanceId").value(ATTENDANCE_ID.toString()))
				.andExpect(jsonPath("$.data.rewards.length()").value(2));
	}

	@Test
	@DisplayName("적용할 출석 정책이 없으면 500 ATTENDANCE-001로 응답한다")
	void mapsMissingPolicyToServerError() throws Exception {
		// given
		when(service.attend(USER_ID))
				.thenThrow(new AttendanceException(AttendanceErrorCode.ATTENDANCE_POLICY_NOT_FOUND));

		// when / then
		mvc.perform(selected(post("/api/v1/attendances")))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("ATTENDANCE-001"));
	}

	@Test
	@DisplayName("사용자 헤더가 없으면 출석하지 않고 401로 응답한다")
	void requiresUserHeaders() throws Exception {
		// given
		// when / then
		mvc.perform(post("/api/v1/attendances"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER-003"));
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("AT02 Swagger에는 사용자 헤더가 표시되고 멤버십은 선택 입력으로 안내된다")
	void documentsUserHeaders() throws Exception {
		// given
		AttendanceController controller = new AttendanceController(service);
		Method method = Arrays.stream(AttendanceController.class.getDeclaredMethods())
				.filter(candidate -> candidate.getName().equals("attend")).findFirst().orElseThrow();

		// when
		Operation operation = new OpenApiConfig().currentUserHeaders()
				.customize(new Operation(), new HandlerMethod(controller, method));

		// then
		assertThat(operation.getParameters()).extracting(Parameter::getName)
				.contains("X-User-ID", "X-User-Role", "X-User-Membership");
		assertThat(operation.getParameters()).filteredOn(parameter -> parameter.getName().equals("X-User-Membership"))
				.extracting(Parameter::getRequired).containsExactly(false);
	}

	private static AttendanceReceipt receipt(boolean created) {
		return new AttendanceReceipt(ATTENDANCE_ID, LocalDate.parse("2026-09-07"), 7, List.of(
				new AttendanceRewardReceipt(DAILY_CLAIM, AttendanceRewardType.DAILY, null, 1, NOW, EXPIRES_AT),
				new AttendanceRewardReceipt(STREAK_CLAIM, AttendanceRewardType.STREAK, 7, 1, NOW, EXPIRES_AT)),
				NOW, created);
	}

	private MockHttpServletRequestBuilder selected(MockHttpServletRequestBuilder request) {
		return request.header("X-User-ID", USER_ID).header("X-User-Role", "USER")
				.header("X-User-Membership", "vip");
	}
}
