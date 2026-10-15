package com.getddo.api.entry.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.getddo.api.common.context.CurrentUserArgumentResolver;
import com.getddo.api.common.exception.GlobalExceptionHandler;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.entry.domain.Entry;
import com.getddo.core.entry.domain.EntryHistoryQuery;
import com.getddo.core.entry.domain.EntryParticipant;
import com.getddo.core.entry.domain.EntryStatus;
import com.getddo.core.entry.domain.EntrySummary;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.exception.EntryException;
import com.getddo.core.entry.service.EntryHistoryService;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EntryHistoryControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final User USER = new User(USER_ID, "사용자", UserRole.USER, UserStatus.ACTIVE, null, null, null, null,
			null, null, null);
	private static final UUID EVENT_ID = UUID.randomUUID();
	private static final Instant AT = Instant.parse("2026-10-14T03:00:00Z");

	private final EntryHistoryService service = mock(EntryHistoryService.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new EntryHistoryController(service))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver(
						new UserService(id -> Optional.of(USER).filter(user -> user.getId().equals(id)))))
				.build();
	}

	/** 멤버십 헤더 없이 사용자 헤더만 보낸다. 응모 이력은 멤버십 조건이 없다. */
	private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
		return request.header("X-User-ID", USER_ID).header("X-User-Role", "USER");
	}

	private static EntrySummary accepted() {
		EntryParticipant participant = new EntryParticipant(UUID.randomUUID(), EVENT_ID, USER_ID, 3, AT);
		return new EntrySummary(Entry.accepted(UUID.randomUUID(), participant,
				Map.of(TicketGrade.GOLD, 2L, TicketGrade.BRONZE, 1L), AT), "가을 이벤트");
	}

	private static EntrySummary rejected() {
		return new EntrySummary(Entry.rejected(UUID.randomUUID(), EVENT_ID, USER_ID, Map.of(TicketGrade.SILVER, 4L),
				EntryErrorCode.TICKET_LIMIT_EXCEEDED, AT.plusSeconds(60)), "가을 이벤트");
	}

	@Test
	@DisplayName("E05는 접수와 거절을 같은 필드 구조로 담고 거절은 차감 0·접수 시각 null·거절 코드를 보여 준다")
	void returnsAcceptedAndRejectedReceipts() throws Exception {
		// given
		when(service.findMyEntriesOfEvent(USER_ID, EVENT_ID, new PageQuery(1, 20)))
				.thenReturn(new PageResult<>(List.of(rejected(), accepted()), 1, 20, 2));
		// when
		// then
		mvc.perform(asUser(get("/api/v1/events/{eventId}/entries/me", EVENT_ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.items[0].status").value("REJECTED"))
				.andExpect(jsonPath("$.data.items[0].eventTitle").value("가을 이벤트"))
				.andExpect(jsonPath("$.data.items[0].requestedTicketCount").value(4))
				.andExpect(jsonPath("$.data.items[0].requestedTicketsByGrade.SILVER").value(4))
				.andExpect(jsonPath("$.data.items[0].requestedTicketsByGrade.GOLD").value(0))
				.andExpect(jsonPath("$.data.items[0].deductedTicketCount").value(0))
				.andExpect(jsonPath("$.data.items[0].deductedTicketsByGrade.SILVER").value(0))
				.andExpect(jsonPath("$.data.items[0].requestedAt").value("2026-10-14T03:01:00Z"))
				.andExpect(jsonPath("$.data.items[0].acceptedAt").doesNotExist())
				.andExpect(jsonPath("$.data.items[0].rejectionCode").value("ENTRY-007"))
				.andExpect(jsonPath("$.data.items[0].rejectionReason")
						.value(EntryErrorCode.TICKET_LIMIT_EXCEEDED.getMessage()))
				.andExpect(jsonPath("$.data.items[1].status").value("ACCEPTED"))
				.andExpect(jsonPath("$.data.items[1].requestedTicketCount").value(3))
				.andExpect(jsonPath("$.data.items[1].deductedTicketsByGrade.GOLD").value(2))
				.andExpect(jsonPath("$.data.items[1].requestedAt").value("2026-10-14T03:00:00Z"))
				.andExpect(jsonPath("$.data.items[1].acceptedAt").value("2026-10-14T03:00:00Z"))
				.andExpect(jsonPath("$.data.items[1].rejectionCode").doesNotExist())
				.andExpect(jsonPath("$.data.items[1].rejectionReason").doesNotExist());
	}

	@Test
	@DisplayName("E05의 페이지 번호나 크기가 범위를 벗어나면 400 COMMON-002이고 조회하지 않는다")
	void rejectsOutOfRangePaging() throws Exception {
		// given
		when(service.findMyEntriesOfEvent(any(), any(), any())).thenReturn(new PageResult<>(List.of(), 1, 20, 0));
		// when
		// then
		for (String[] invalid : new String[][] {{"page", "0"}, {"size", "0"}, {"size", "101"}}) {
			mvc.perform(asUser(get("/api/v1/events/{eventId}/entries/me", EVENT_ID)).param(invalid[0], invalid[1]))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON-002"));
		}
		mvc.perform(asUser(get("/api/v1/events/{eventId}/entries/me", EVENT_ID)).param("size", "100"))
				.andExpect(status().isOk());
		mvc.perform(asUser(get("/api/v1/events/{eventId}/entries/me", EVENT_ID)).param("size", "1"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("E05에서 없는 이벤트는 404 ENTRY-004이다")
	void missingEventIsNotFound() throws Exception {
		// given
		when(service.findMyEntriesOfEvent(any(), any(), any()))
				.thenThrow(new EntryException(EntryErrorCode.EVENT_NOT_FOUND));
		// when
		// then
		mvc.perform(asUser(get("/api/v1/events/{eventId}/entries/me", EVENT_ID)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ENTRY-004"));
	}

	@Test
	@DisplayName("E06은 조건을 서비스에 넘기고 시간대가 있는 시각을 UTC로 바꾼다")
	void passesFiltersToService() throws Exception {
		// given
		when(service.findMyEntries(any(), any(), any())).thenReturn(new PageResult<>(List.of(), 2, 5, 0));
		// when
		mvc.perform(asUser(get("/api/v1/users/me/entries"))
						.param("page", "2").param("size", "5").param("eventId", EVENT_ID.toString())
						.param("status", "REJECTED").param("from", "2026-10-14T12:00:00+09:00")
						.param("to", "2026-10-15T00:00:00Z"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(2));
		// then
		ArgumentCaptor<EntryHistoryQuery> query = ArgumentCaptor.forClass(EntryHistoryQuery.class);
		verify(service).findMyEntries(org.mockito.ArgumentMatchers.eq(USER_ID), query.capture(),
				org.mockito.ArgumentMatchers.eq(new PageQuery(2, 5)));
		assertThat(query.getValue().getEventId()).isEqualTo(EVENT_ID);
		assertThat(query.getValue().getStatus()).isEqualTo(EntryStatus.REJECTED);
		assertThat(query.getValue().getFrom()).isEqualTo(Instant.parse("2026-10-14T03:00:00Z"));
		assertThat(query.getValue().getTo()).isEqualTo(Instant.parse("2026-10-15T00:00:00Z"));
	}

	@Test
	@DisplayName("E06은 조건을 생략하면 첫 페이지 20건을 전체 조건으로 조회한다")
	void usesDefaults() throws Exception {
		// given
		when(service.findMyEntries(any(), any(), any())).thenReturn(new PageResult<>(List.of(), 1, 20, 0));
		// when
		mvc.perform(asUser(get("/api/v1/users/me/entries"))).andExpect(status().isOk());
		// then
		ArgumentCaptor<EntryHistoryQuery> query = ArgumentCaptor.forClass(EntryHistoryQuery.class);
		verify(service).findMyEntries(org.mockito.ArgumentMatchers.eq(USER_ID), query.capture(),
				org.mockito.ArgumentMatchers.eq(new PageQuery(1, 20)));
		assertThat(query.getValue().getEventId()).isNull();
		assertThat(query.getValue().getStatus()).isNull();
		assertThat(query.getValue().getFrom()).isNull();
		assertThat(query.getValue().getTo()).isNull();
	}

	@Test
	@DisplayName("E06의 알 수 없는 상태·시각 형식·이벤트 ID는 400 COMMON-005이고 기간이 뒤집히면 400 COMMON-002이며 조회하지 않는다")
	void rejectsInvalidFilters() throws Exception {
		// given
		// when
		// then
		mvc.perform(asUser(get("/api/v1/users/me/entries")).param("status", "PENDING"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-005"));
		mvc.perform(asUser(get("/api/v1/users/me/entries")).param("from", "2026-10-14"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-005"));
		mvc.perform(asUser(get("/api/v1/users/me/entries")).param("eventId", "not-a-uuid"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-005"));
		mvc.perform(asUser(get("/api/v1/users/me/entries"))
						.param("from", "2026-10-15T00:00:00Z").param("to", "2026-10-14T00:00:00Z"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-002"));
		mvc.perform(asUser(get("/api/v1/users/me/entries"))
						.param("from", "2026-10-14T00:00:00Z").param("to", "2026-10-14T00:00:00Z"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-002"));
		mvc.perform(asUser(get("/api/v1/users/me/entries")).param("page", "0"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-002"));
		verifyNoInteractions(service);
	}
}
