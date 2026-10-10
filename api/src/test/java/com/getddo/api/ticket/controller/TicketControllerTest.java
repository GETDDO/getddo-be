package com.getddo.api.ticket.controller;

import java.time.Instant;
import java.util.Arrays;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.getddo.api.common.config.OpenApiConfig;
import com.getddo.api.common.context.CurrentUserArgumentResolver;
import com.getddo.api.common.exception.GlobalExceptionHandler;
import com.getddo.core.common.pagination.CursorResult;
import com.getddo.core.ticket.domain.MyTickets;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketHistoryFilter;
import com.getddo.core.ticket.domain.TicketHistoryView;
import com.getddo.core.ticket.domain.TicketHolding;
import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.ticket.service.TicketQueryService;
import com.getddo.core.user.domain.Membership;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketControllerTest {
	private static final UUID USER_ID = UUID.randomUUID();
	private static final User USER = new User(USER_ID, "사용자", UserRole.USER, UserStatus.ACTIVE,
			Membership.VIP, null, null, null, null, null, null);
	/** 2026-09-15 12:00 KST. */
	private static final Instant NOW = Instant.parse("2026-09-15T03:00:00Z");
	private final TicketQueryService service = mock(TicketQueryService.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new TicketController(service))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver(
						new UserService(id -> Optional.of(USER).filter(user -> user.id().equals(id)))))
				.build();
	}

	@Test
	@DisplayName("T01은 사용 가능 장수와 등급별 장수, 등급·만료 시각별 묶음, 조회 시각을 UTC로 반환한다")
	void returnsMyTickets() throws Exception {
		// given
		when(service.getMyTickets(USER_ID)).thenReturn(MyTickets.of(List.of(
				new TicketHolding(TicketGrade.BRONZE, Instant.parse("2026-09-30T15:00:00Z"), 3),
				new TicketHolding(TicketGrade.GOLD, Instant.parse("2026-10-31T15:00:00Z"), 1)), NOW));

		// when / then
		mvc.perform(selected(get("/api/v1/tickets/me")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.availableCount").value(4))
				.andExpect(jsonPath("$.data.serverTime").value("2026-09-15T03:00:00Z"))
				.andExpect(jsonPath("$.data.countByGrade.BRONZE").value(3))
				.andExpect(jsonPath("$.data.countByGrade.SILVER").value(0))
				.andExpect(jsonPath("$.data.countByGrade.GOLD").value(1))
				.andExpect(jsonPath("$.data.holdings[0].grade").value("BRONZE"))
				.andExpect(jsonPath("$.data.holdings[0].expiresAt").value("2026-09-30T15:00:00Z"))
				.andExpect(jsonPath("$.data.holdings[0].count").value(3))
				.andExpect(jsonPath("$.data.holdings[1].grade").value("GOLD"))
				.andExpect(jsonPath("$.data.holdings[1].count").value(1));
	}

	@Test
	@DisplayName("T02는 조회 조건을 서비스에 넘기고 이력·다음 커서·전체 건수를 반환한다")
	void returnsMyHistory() throws Exception {
		// given
		TicketHistoryView grant = new TicketHistoryView(UUID.randomUUID(), UUID.randomUUID(),
				TicketOperationType.GRANT, TicketGrade.BRONZE, TicketStatus.AVAILABLE,
				Instant.parse("2026-09-30T15:00:00Z"), "출석 보상", NOW, null, null, null, null,
				LocalDate.parse("2026-09-15"), null, null);
		when(service.getMyHistory(eq(USER_ID), any(), eq("next-page"), eq(10)))
				.thenReturn(new CursorResult<>(List.of(grant), "after-grant", 7));

		// when / then
		mvc.perform(selected(get("/api/v1/tickets/histories/me"))
						.param("cursor", "next-page").param("size", "10").param("operationType", "GRANT")
						.param("from", "2026-09-01T00:00:00+09:00").param("to", "2026-10-01T00:00:00Z"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(grant.getId().toString()))
				.andExpect(jsonPath("$.data.items[0].ticketId").value(grant.getTicketId().toString()))
				.andExpect(jsonPath("$.data.items[0].operationType").value("GRANT"))
				.andExpect(jsonPath("$.data.items[0].grade").value("BRONZE"))
				.andExpect(jsonPath("$.data.items[0].status").value("AVAILABLE"))
				.andExpect(jsonPath("$.data.items[0].reason").value("출석 보상"))
				.andExpect(jsonPath("$.data.items[0].createdAt").value("2026-09-15T03:00:00Z"))
				.andExpect(jsonPath("$.data.items[0].expiresAt").value("2026-09-30T15:00:00Z"))
				.andExpect(jsonPath("$.data.items[0].attendanceDate").value("2026-09-15"))
				.andExpect(jsonPath("$.data.items[0].missionId").value(nullValue()))
				.andExpect(jsonPath("$.data.nextCursor").value("after-grant"))
				.andExpect(jsonPath("$.data.totalElements").value(7));
		ArgumentCaptor<TicketHistoryFilter> filter = ArgumentCaptor.forClass(TicketHistoryFilter.class);
		verify(service).getMyHistory(eq(USER_ID), filter.capture(), eq("next-page"), eq(10));
		assertThat(filter.getValue().getOperationType()).isEqualTo(TicketOperationType.GRANT);
		assertThat(filter.getValue().getFrom()).isEqualTo(Instant.parse("2026-08-31T15:00:00Z"));
		assertThat(filter.getValue().getTo()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
	}

	@Test
	@DisplayName("T02 조건을 생략하면 첫 페이지 20건을 전체 유형·기간 제한 없이 조회한다")
	void usesDefaultsWhenParametersAreOmitted() throws Exception {
		// given
		when(service.getMyHistory(eq(USER_ID), any(), isNull(), eq(20)))
				.thenReturn(new CursorResult<>(List.of(), null, 0));

		// when / then
		mvc.perform(selected(get("/api/v1/tickets/histories/me")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
				.andExpect(jsonPath("$.data.totalElements").value(0));
		ArgumentCaptor<TicketHistoryFilter> filter = ArgumentCaptor.forClass(TicketHistoryFilter.class);
		verify(service).getMyHistory(eq(USER_ID), filter.capture(), isNull(), eq(20));
		assertThat(filter.getValue().getOperationType()).isNull();
		assertThat(filter.getValue().getFrom()).isNull();
		assertThat(filter.getValue().getTo()).isNull();
	}

	@Test
	@DisplayName("서비스의 조회 조건 오류는 400 TICKET-004로 응답한다")
	void mapsInvalidQueryToBadRequest() throws Exception {
		// given
		when(service.getMyHistory(eq(USER_ID), any(), any(), anyInt()))
				.thenThrow(new TicketException(TicketErrorCode.TICKET_INVALID_HISTORY_QUERY));

		// when / then
		mvc.perform(selected(get("/api/v1/tickets/histories/me")).param("size", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TICKET-004"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"operationType=PURCHASE", "from=2026-09-01T00:00:00", "to=yesterday", "size=many"})
	@DisplayName("처리 유형·시각·개수를 해석할 수 없으면 서비스를 부르지 않고 400 COMMON-005로 응답한다")
	void rejectsUnparsableParameters(String parameter) throws Exception {
		// given
		String[] pair = parameter.split("=", 2);

		// when / then
		mvc.perform(selected(get("/api/v1/tickets/histories/me")).param(pair[0], pair[1]))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("사용자 헤더가 없으면 조회하지 않고 401로 응답한다")
	void requiresUserHeaders() throws Exception {
		// given
		// when / then
		mvc.perform(get("/api/v1/tickets/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER-003"));
		mvc.perform(get("/api/v1/tickets/histories/me"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("T01·T02 Swagger에는 사용자 헤더가 표시되고 멤버십은 선택 입력으로 안내된다")
	void documentsUserHeaders() {
		// given
		TicketController controller = new TicketController(service);

		// when / then
		for (String methodName : new String[]{"findMyTickets", "findMyHistory"}) {
			var method = Arrays.stream(TicketController.class.getDeclaredMethods())
					.filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
			Operation operation = new OpenApiConfig().currentUserHeaders()
					.customize(new Operation(), new HandlerMethod(controller, method));
			assertThat(operation.getParameters()).extracting(Parameter::getName)
					.contains("X-User-ID", "X-User-Role", "X-User-Membership");
			assertThat(operation.getParameters()).filteredOn(parameter -> parameter.getName().equals("X-User-Membership"))
					.extracting(Parameter::getRequired).containsExactly(false);
		}
	}

	private MockHttpServletRequestBuilder selected(MockHttpServletRequestBuilder request) {
		return request.header("X-User-ID", USER_ID).header("X-User-Role", "USER")
				.header("X-User-Membership", "vip");
	}
}
