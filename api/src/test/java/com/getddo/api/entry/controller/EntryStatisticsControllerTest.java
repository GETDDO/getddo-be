package com.getddo.api.entry.controller;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.getddo.api.common.context.CurrentUserArgumentResolver;
import com.getddo.api.common.exception.GlobalExceptionHandler;
import com.getddo.core.entry.domain.EntryStatistics;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.exception.EntryException;
import com.getddo.core.entry.service.EntryStatisticsService;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.user.service.UserService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EntryStatisticsControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final User USER = new User(USER_ID, "사용자", UserRole.USER, UserStatus.ACTIVE, null, null, null, null,
			null, null, null);
	private static final UUID EVENT_ID = UUID.randomUUID();

	private final EntryStatisticsService service = mock(EntryStatisticsService.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new EntryStatisticsController(service))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver(
						new UserService(id -> Optional.of(USER).filter(user -> user.getId().equals(id)))))
				.build();
	}

	/** 멤버십 헤더 없이 사용자 헤더만 보낸다. 응모 현황은 멤버십 조건이 없다. */
	private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
		return request.header("X-User-ID", USER_ID).header("X-User-Role", "USER");
	}

	@Test
	@DisplayName("응모 현황은 스펙의 필드 다섯 개를 봉투에 담아 돌려준다")
	void returnsStatisticsEnvelope() throws Exception {
		// given
		when(service.getStatistics(USER_ID, EVENT_ID))
				.thenReturn(new EntryStatistics(EVENT_ID, 3, 11, 4, Instant.parse("2026-10-14T03:00:00Z")));
		// when
		// then
		mvc.perform(asUser(get("/api/v1/events/{eventId}/statistics", EVENT_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.eventId").value(EVENT_ID.toString()))
				.andExpect(jsonPath("$.data.participantCount").value(3))
				.andExpect(jsonPath("$.data.totalSpentTicketCount").value(11))
				.andExpect(jsonPath("$.data.mySpentTicketCount").value(4))
				.andExpect(jsonPath("$.data.serverTime").value("2026-10-14T03:00:00Z"));
	}

	@Test
	@DisplayName("없는 이벤트는 404 ENTRY-004이다")
	void missingEventIsNotFound() throws Exception {
		// given
		when(service.getStatistics(USER_ID, EVENT_ID)).thenThrow(new EntryException(EntryErrorCode.EVENT_NOT_FOUND));
		// when
		// then
		mvc.perform(asUser(get("/api/v1/events/{eventId}/statistics", EVENT_ID)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ENTRY-004"));
	}

	@Test
	@DisplayName("이벤트 ID가 UUID가 아니면 400 COMMON-005이다")
	void malformedEventIdIsBadRequest() throws Exception {
		// given
		// when
		// then
		mvc.perform(asUser(get("/api/v1/events/{eventId}/statistics", "not-a-uuid")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
	}
}
