package com.getddo.core.ticket.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.getddo.core.common.pagination.CursorQuery;
import com.getddo.core.common.pagination.CursorResult;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.ticket.domain.MyTickets;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketHistoryCursor;
import com.getddo.core.ticket.domain.TicketHistoryFilter;
import com.getddo.core.ticket.domain.TicketHistoryView;
import com.getddo.core.ticket.domain.TicketHolding;
import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.ticket.repository.TicketQueryRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketQueryServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-01T00:30:00Z");
	private static final UUID USER_ID = UUID.randomUUID();

	@Mock
	private TicketQueryRepository queryRepository;

	private TicketQueryService service;

	@BeforeEach
	void setUp() {
		service = new TicketQueryService(queryRepository, new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
	}

	private static TicketHistoryView grant(Instant createdAt) {
		return new TicketHistoryView(UUID.randomUUID(), UUID.randomUUID(), TicketOperationType.GRANT,
				TicketGrade.BRONZE, TicketStatus.AVAILABLE, createdAt.plusSeconds(3600), "보상", createdAt, null,
				null, null, null, null, null, null);
	}

	private static void assertInvalidQuery(Runnable call) {
		assertThatThrownBy(call::run)
				.isInstanceOf(TicketException.class)
				.extracting(error -> ((TicketException) error).getErrorCode())
				.isEqualTo(TicketErrorCode.TICKET_INVALID_HISTORY_QUERY);
	}

	@Nested
	@DisplayName("getMyTickets")
	class GetMyTickets {

		@Test
		@DisplayName("조회 시각을 한 번 구해 저장소에 넘기고 묶음으로 등급별 장수를 센다")
		void countsByGrade() {
			// given
			TicketHolding bronze = new TicketHolding(TicketGrade.BRONZE, Instant.parse("2026-10-31T15:00:00Z"), 3);
			TicketHolding gold = new TicketHolding(TicketGrade.GOLD, Instant.parse("2026-10-31T15:00:00Z"), 1);
			when(queryRepository.findHoldings(USER_ID, NOW)).thenReturn(List.of(bronze, gold));
			// when
			MyTickets result = service.getMyTickets(USER_ID);
			// then
			assertThat(result.getServerTime()).isEqualTo(NOW);
			assertThat(result.getAvailableCount()).isEqualTo(4);
			assertThat(result.getCountByGrade()).containsEntry(TicketGrade.BRONZE, 3L)
					.containsEntry(TicketGrade.SILVER, 0L).containsEntry(TicketGrade.GOLD, 1L);
			assertThat(result.getHoldings()).containsExactly(bronze, gold);
		}

		@Test
		@DisplayName("보유한 응모권이 없으면 장수 0과 빈 목록이다")
		void noTickets() {
			// given
			when(queryRepository.findHoldings(USER_ID, NOW)).thenReturn(List.of());
			// when
			MyTickets result = service.getMyTickets(USER_ID);
			// then
			assertThat(result.getAvailableCount()).isZero();
			assertThat(result.getCountByGrade().values()).containsOnly(0L);
			assertThat(result.getHoldings()).isEmpty();
		}
	}

	@Nested
	@DisplayName("getMyHistory")
	class GetMyHistory {

		@Test
		@DisplayName("요청보다 1건 더 조회해 다음 페이지가 있으면 마지막 항목의 커서를 돌려준다")
		void returnsNextCursorWhenMoreRows() {
			// given
			List<TicketHistoryView> rows = List.of(grant(NOW), grant(NOW.minusSeconds(1)),
					grant(NOW.minusSeconds(2)));
			when(queryRepository.findHistory(eq(USER_ID), any(), isNull(), eq(3))).thenReturn(rows);
			when(queryRepository.countHistory(eq(USER_ID), any())).thenReturn(10L);
			// when
			CursorResult<TicketHistoryView> result =
					service.getMyHistory(USER_ID, TicketHistoryFilter.none(), new CursorQuery(null, 2));
			// then
			assertThat(result.getItems()).containsExactly(rows.get(0), rows.get(1));
			assertThat(result.getNextCursor()).isEqualTo(rows.get(1).cursor().encode());
			assertThat(result.getTotalElements()).isEqualTo(10);
		}

		@Test
		@DisplayName("마지막 페이지면 다음 커서가 없다")
		void lastPageHasNoCursor() {
			// given
			List<TicketHistoryView> rows = List.of(grant(NOW));
			when(queryRepository.findHistory(eq(USER_ID), any(), isNull(), eq(3))).thenReturn(rows);
			when(queryRepository.countHistory(eq(USER_ID), any())).thenReturn(1L);
			// when
			CursorResult<TicketHistoryView> result =
					service.getMyHistory(USER_ID, TicketHistoryFilter.none(), new CursorQuery(null, 2));
			// then
			assertThat(result.getItems()).containsExactly(rows.get(0));
			assertThat(result.getNextCursor()).isNull();
		}

		@Test
		@DisplayName("받은 커서를 해석해 그 뒤부터 조회하고 조회 조건을 그대로 넘긴다")
		void passesDecodedCursorAndFilter() {
			// given
			TicketHistoryCursor cursor = new TicketHistoryCursor(NOW, UUID.randomUUID());
			TicketHistoryFilter filter = TicketHistoryFilter.of(TicketOperationType.GRANT, null, NOW);
			when(queryRepository.findHistory(eq(USER_ID), eq(filter), refEq(cursor), eq(21))).thenReturn(List.of());
			// when
			service.getMyHistory(USER_ID, filter, new CursorQuery(cursor.encode(), 20));
			// then
			verify(queryRepository).findHistory(eq(USER_ID), eq(filter), refEq(cursor), eq(21));
			verify(queryRepository).countHistory(USER_ID, filter);
		}

		@Test
		@DisplayName("조회 개수가 100을 넘으면 거절한다")
		void rejectsTooLargeSize() {
			// given
			CursorQuery page = new CursorQuery(null, 101);
			// when
			// then
			assertInvalidQuery(() -> service.getMyHistory(USER_ID, TicketHistoryFilter.none(), page));
			verifyNoInteractions(queryRepository);
		}

		@Test
		@DisplayName("조회 개수 100은 허용한다")
		void allowsMaxSize() {
			// given
			when(queryRepository.findHistory(eq(USER_ID), any(), isNull(), anyInt())).thenReturn(List.of());
			// when
			service.getMyHistory(USER_ID, TicketHistoryFilter.none(), new CursorQuery(null, 100));
			// then
			verify(queryRepository).findHistory(eq(USER_ID), any(), isNull(), eq(101));
		}

		@ParameterizedTest
		@ValueSource(ints = {0, -1})
		@DisplayName("요청 값으로 조회할 때 조회 개수가 1 미만이면 TICKET-004로 거절한다")
		void rejectsNonPositiveRequestSize(int size) {
			// given
			// when
			// then
			assertInvalidQuery(() -> service.getMyHistory(USER_ID, TicketHistoryFilter.none(), null, size));
			verifyNoInteractions(queryRepository);
		}

		@Test
		@DisplayName("요청 값으로 조회할 때 빈 커서는 TICKET-004로 거절한다")
		void rejectsBlankRequestCursor() {
			// given
			// when
			// then
			assertInvalidQuery(() -> service.getMyHistory(USER_ID, TicketHistoryFilter.none(), " ", 20));
			verifyNoInteractions(queryRepository);
		}

		@Test
		@DisplayName("요청 값으로 조회하면 같은 커서 조회로 이어진다")
		void requestValuesUseCursorQuery() {
			// given
			when(queryRepository.findHistory(eq(USER_ID), any(), isNull(), eq(21))).thenReturn(List.of());
			// when
			service.getMyHistory(USER_ID, TicketHistoryFilter.none(), null, 20);
			// then
			verify(queryRepository).findHistory(eq(USER_ID), any(), isNull(), eq(21));
		}

		@Test
		@DisplayName("시작 시각이 끝 시각보다 앞서지 않으면 거절한다")
		void rejectsEmptyPeriod() {
			// given
			TicketHistoryFilter same = TicketHistoryFilter.of(null, NOW, NOW);
			TicketHistoryFilter reversed = TicketHistoryFilter.of(null, NOW, NOW.minusSeconds(1));
			CursorQuery page = new CursorQuery(null, 20);
			// when
			// then
			assertInvalidQuery(() -> service.getMyHistory(USER_ID, same, page));
			assertInvalidQuery(() -> service.getMyHistory(USER_ID, reversed, page));
			verifyNoInteractions(queryRepository);
		}

		@Test
		@DisplayName("해석할 수 없는 커서는 저장소를 조회하지 않고 거절한다")
		void rejectsMalformedCursor() {
			// given
			CursorQuery page = new CursorQuery("not-a-cursor", 20);
			// when
			// then
			assertInvalidQuery(() -> service.getMyHistory(USER_ID, TicketHistoryFilter.none(), page));
			verifyNoInteractions(queryRepository);
		}

		@Test
		@DisplayName("조회 조건이나 페이지가 없으면 거절한다")
		void rejectsMissingArguments() {
			// given
			// when
			// then
			assertInvalidQuery(() -> service.getMyHistory(USER_ID, null, new CursorQuery(null, 20)));
			assertInvalidQuery(() -> service.getMyHistory(USER_ID, TicketHistoryFilter.none(), null));
			verifyNoInteractions(queryRepository);
		}
	}
}
