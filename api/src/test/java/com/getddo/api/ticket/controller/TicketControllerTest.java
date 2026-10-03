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
import com.getddo.core.ticket.domain.MyTicketWallets;
import com.getddo.core.ticket.domain.TicketLedgerFilter;
import com.getddo.core.ticket.domain.TicketTransactionType;
import com.getddo.core.ticket.domain.TicketTransactionView;
import com.getddo.core.ticket.domain.TicketWallet;
import com.getddo.core.ticket.domain.TicketWalletStatus;
import com.getddo.core.ticket.domain.TicketWalletView;
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
	@DisplayName("T01은 만료월을 YYYY-MM으로, 시각을 UTC로 내보내고 조회 시각 기준 상태와 사용 가능 잔액을 반환한다")
	void returnsMyWallets() throws Exception {
		// given
		TicketWallet september = new TicketWallet(UUID.randomUUID(), USER_ID, LocalDate.parse("2026-09-01"),
				Instant.parse("2026-09-01T01:00:00Z"), Instant.parse("2026-09-30T15:00:00Z"), 3,
				TicketWalletStatus.ACTIVE, 2);
		TicketWallet august = new TicketWallet(UUID.randomUUID(), USER_ID, LocalDate.parse("2026-08-01"),
				Instant.parse("2026-08-03T01:00:00Z"), Instant.parse("2026-08-31T15:00:00Z"), 5,
				TicketWalletStatus.ACTIVE, 1);
		when(service.getMyWallets(USER_ID)).thenReturn(MyTicketWallets.of(
				List.of(TicketWalletView.of(september, NOW), TicketWalletView.of(august, NOW)), NOW));

		// when / then
		mvc.perform(selected(get("/api/v1/tickets/wallets/me")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.availableBalance").value(3))
				.andExpect(jsonPath("$.data.serverTime").value("2026-09-15T03:00:00Z"))
				.andExpect(jsonPath("$.data.wallets[0].id").value(september.getId().toString()))
				.andExpect(jsonPath("$.data.wallets[0].expiryMonth").value("2026-09"))
				.andExpect(jsonPath("$.data.wallets[0].validFrom").value("2026-09-01T01:00:00Z"))
				.andExpect(jsonPath("$.data.wallets[0].expiresAt").value("2026-09-30T15:00:00Z"))
				.andExpect(jsonPath("$.data.wallets[0].balance").value(3))
				.andExpect(jsonPath("$.data.wallets[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.wallets[1].expiryMonth").value("2026-08"))
				.andExpect(jsonPath("$.data.wallets[1].balance").value(5))
				.andExpect(jsonPath("$.data.wallets[1].status").value("EXPIRED"));
	}

	@Test
	@DisplayName("T02는 조회 조건을 서비스에 넘기고 이력·다음 커서·전체 건수를 반환한다")
	void returnsMyLedger() throws Exception {
		// given
		TicketTransactionView grant = new TicketTransactionView(UUID.randomUUID(), UUID.randomUUID(),
				TicketTransactionType.GRANT, 1, 4, "출석 보상", NOW, Instant.parse("2026-09-30T15:00:00Z"), null,
				null, null, null, LocalDate.parse("2026-09-15"), null, null);
		when(service.getMyLedger(eq(USER_ID), any(), eq("next-page"), eq(10)))
				.thenReturn(new CursorResult<>(List.of(grant), "after-grant", 7));

		// when / then
		mvc.perform(selected(get("/api/v1/tickets/ledger/me"))
						.param("cursor", "next-page").param("size", "10").param("transactionType", "GRANT")
						.param("from", "2026-09-01T00:00:00+09:00").param("to", "2026-10-01T00:00:00Z"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(grant.getId().toString()))
				.andExpect(jsonPath("$.data.items[0].transactionType").value("GRANT"))
				.andExpect(jsonPath("$.data.items[0].quantity").value(1))
				.andExpect(jsonPath("$.data.items[0].balanceAfter").value(4))
				.andExpect(jsonPath("$.data.items[0].reason").value("출석 보상"))
				.andExpect(jsonPath("$.data.items[0].createdAt").value("2026-09-15T03:00:00Z"))
				.andExpect(jsonPath("$.data.items[0].expiresAt").value("2026-09-30T15:00:00Z"))
				.andExpect(jsonPath("$.data.items[0].attendanceDate").value("2026-09-15"))
				.andExpect(jsonPath("$.data.items[0].missionId").value(nullValue()))
				.andExpect(jsonPath("$.data.nextCursor").value("after-grant"))
				.andExpect(jsonPath("$.data.totalElements").value(7));
		ArgumentCaptor<TicketLedgerFilter> filter = ArgumentCaptor.forClass(TicketLedgerFilter.class);
		verify(service).getMyLedger(eq(USER_ID), filter.capture(), eq("next-page"), eq(10));
		assertThat(filter.getValue().getTransactionType()).isEqualTo(TicketTransactionType.GRANT);
		assertThat(filter.getValue().getFrom()).isEqualTo(Instant.parse("2026-08-31T15:00:00Z"));
		assertThat(filter.getValue().getTo()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
	}

	@Test
	@DisplayName("T02 조건을 생략하면 첫 페이지 20건을 전체 유형·기간 제한 없이 조회한다")
	void usesDefaultsWhenParametersAreOmitted() throws Exception {
		// given
		when(service.getMyLedger(eq(USER_ID), any(), isNull(), eq(20)))
				.thenReturn(new CursorResult<>(List.of(), null, 0));

		// when / then
		mvc.perform(selected(get("/api/v1/tickets/ledger/me")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
				.andExpect(jsonPath("$.data.totalElements").value(0));
		ArgumentCaptor<TicketLedgerFilter> filter = ArgumentCaptor.forClass(TicketLedgerFilter.class);
		verify(service).getMyLedger(eq(USER_ID), filter.capture(), isNull(), eq(20));
		assertThat(filter.getValue().getTransactionType()).isNull();
		assertThat(filter.getValue().getFrom()).isNull();
		assertThat(filter.getValue().getTo()).isNull();
	}

	@Test
	@DisplayName("서비스의 조회 조건 오류는 400 TICKET-004로 응답한다")
	void mapsInvalidQueryToBadRequest() throws Exception {
		// given
		when(service.getMyLedger(eq(USER_ID), any(), any(), anyInt()))
				.thenThrow(new TicketException(TicketErrorCode.TICKET_INVALID_LEDGER_QUERY));

		// when / then
		mvc.perform(selected(get("/api/v1/tickets/ledger/me")).param("size", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TICKET-004"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"transactionType=PURCHASE", "from=2026-09-01T00:00:00", "to=yesterday", "size=many"})
	@DisplayName("거래 유형·시각·개수를 해석할 수 없으면 서비스를 부르지 않고 400 COMMON-005로 응답한다")
	void rejectsUnparsableParameters(String parameter) throws Exception {
		// given
		String[] pair = parameter.split("=", 2);

		// when / then
		mvc.perform(selected(get("/api/v1/tickets/ledger/me")).param(pair[0], pair[1]))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("사용자 헤더가 없으면 조회하지 않고 401로 응답한다")
	void requiresUserHeaders() throws Exception {
		// given
		// when / then
		mvc.perform(get("/api/v1/tickets/wallets/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER-003"));
		mvc.perform(get("/api/v1/tickets/ledger/me"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("T01·T02 Swagger에는 사용자 헤더가 표시되고 멤버십은 선택 입력으로 안내된다")
	void documentsUserHeaders() {
		// given
		TicketController controller = new TicketController(service);

		// when / then
		for (String methodName : new String[]{"findMyWallets", "findMyLedger"}) {
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
