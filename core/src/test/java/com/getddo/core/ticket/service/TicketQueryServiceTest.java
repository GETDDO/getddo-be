package com.getddo.core.ticket.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.CursorQuery;
import com.getddo.core.common.pagination.CursorResult;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.ticket.domain.MyTicketWallets;
import com.getddo.core.ticket.domain.TicketLedgerCursor;
import com.getddo.core.ticket.domain.TicketLedgerFilter;
import com.getddo.core.ticket.domain.TicketTransactionType;
import com.getddo.core.ticket.domain.TicketTransactionView;
import com.getddo.core.ticket.domain.TicketWallet;
import com.getddo.core.ticket.domain.TicketWalletStatus;
import com.getddo.core.ticket.domain.TicketWalletView;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.repository.TicketQueryRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

	private static TicketWallet wallet(String expiryMonth, String expiresAt, long balance, TicketWalletStatus status) {
		return new TicketWallet(UUID.randomUUID(), USER_ID, LocalDate.parse(expiryMonth),
				Instant.parse("2026-08-01T00:00:00Z"), Instant.parse(expiresAt), balance, status, 1);
	}

	private static TicketTransactionView grant(Instant createdAt) {
		return TicketTransactionView.builder()
				.id(UUID.randomUUID())
				.walletId(UUID.randomUUID())
				.transactionType(TicketTransactionType.GRANT)
				.quantity(1)
				.balanceAfter(1)
				.reason("보상")
				.createdAt(createdAt)
				.build();
	}

	private static void assertInvalidQuery(Runnable call) {
		assertThatThrownBy(call::run)
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(TicketErrorCode.TICKET_INVALID_LEDGER_QUERY);
	}

	@Nested
	@DisplayName("getMyWallets")
	class GetMyWallets {

		@Test
		@DisplayName("만료 시각이 지난 지갑은 ACTIVE로 저장돼 있어도 만료로 보여 주고 사용 가능 잔액에서 뺀다")
		void excludesWalletsExpiredByTime() {
			// given: 조회 시각은 10/1 09:30 KST. 9월 지갑은 방금 만료됐지만 만료 처리가 아직 안 됐다
			TicketWallet october = wallet("2026-10-01", "2026-10-31T15:00:00Z", 2, TicketWalletStatus.ACTIVE);
			TicketWallet septemberNotProcessed =
					wallet("2026-09-01", "2026-09-30T15:00:00Z", 5, TicketWalletStatus.ACTIVE);
			TicketWallet august = wallet("2026-08-01", "2026-08-31T15:00:00Z", 3, TicketWalletStatus.EXPIRED);
			when(queryRepository.findWallets(USER_ID)).thenReturn(List.of(october, septemberNotProcessed, august));
			// when
			MyTicketWallets result = service.getMyWallets(USER_ID);
			// then
			assertThat(result.getServerTime()).isEqualTo(NOW);
			assertThat(result.getAvailableBalance()).isEqualTo(2);
			assertThat(result.getWallets()).extracting(TicketWalletView::getStatus).containsExactly(
					TicketWalletStatus.ACTIVE, TicketWalletStatus.EXPIRED, TicketWalletStatus.EXPIRED);
			assertThat(result.getWallets()).extracting(TicketWalletView::getBalance).containsExactly(2L, 5L, 3L);
			assertThat(result.getWallets()).extracting(TicketWalletView::getId)
					.containsExactly(october.getId(), septemberNotProcessed.getId(), august.getId());
		}

		@Test
		@DisplayName("만료 시각과 조회 시각이 같으면 이미 만료된 것으로 본다")
		void expiresAtBoundary() {
			// given
			TicketWallet boundary = wallet("2026-09-01", NOW.toString(), 4, TicketWalletStatus.ACTIVE);
			when(queryRepository.findWallets(USER_ID)).thenReturn(List.of(boundary));
			// when
			MyTicketWallets result = service.getMyWallets(USER_ID);
			// then
			assertThat(result.getAvailableBalance()).isZero();
			assertThat(result.getWallets().get(0).getStatus()).isEqualTo(TicketWalletStatus.EXPIRED);
		}

		@Test
		@DisplayName("지갑이 없으면 사용 가능 잔액 0과 빈 목록이다")
		void noWallets() {
			// given
			when(queryRepository.findWallets(USER_ID)).thenReturn(List.of());
			// when
			MyTicketWallets result = service.getMyWallets(USER_ID);
			// then
			assertThat(result.getAvailableBalance()).isZero();
			assertThat(result.getWallets()).isEmpty();
		}
	}

	@Nested
	@DisplayName("getMyLedger")
	class GetMyLedger {

		@Test
		@DisplayName("요청보다 1건 더 조회해 다음 페이지가 있으면 마지막 항목의 커서를 돌려준다")
		void returnsNextCursorWhenMoreRows() {
			// given
			List<TicketTransactionView> rows = List.of(grant(NOW), grant(NOW.minusSeconds(1)),
					grant(NOW.minusSeconds(2)));
			when(queryRepository.findLedger(eq(USER_ID), any(), isNull(), eq(3))).thenReturn(rows);
			when(queryRepository.countLedger(eq(USER_ID), any())).thenReturn(10L);
			// when
			CursorResult<TicketTransactionView> result =
					service.getMyLedger(USER_ID, TicketLedgerFilter.none(), new CursorQuery(null, 2));
			// then
			assertThat(result.getItems()).containsExactly(rows.get(0), rows.get(1));
			assertThat(result.getNextCursor()).isEqualTo(rows.get(1).cursor().encode());
			assertThat(result.getTotalElements()).isEqualTo(10);
		}

		@Test
		@DisplayName("마지막 페이지면 다음 커서가 없다")
		void lastPageHasNoCursor() {
			// given
			List<TicketTransactionView> rows = List.of(grant(NOW));
			when(queryRepository.findLedger(eq(USER_ID), any(), isNull(), eq(3))).thenReturn(rows);
			when(queryRepository.countLedger(eq(USER_ID), any())).thenReturn(1L);
			// when
			CursorResult<TicketTransactionView> result =
					service.getMyLedger(USER_ID, TicketLedgerFilter.none(), new CursorQuery(null, 2));
			// then
			assertThat(result.getItems()).containsExactly(rows.get(0));
			assertThat(result.getNextCursor()).isNull();
		}

		@Test
		@DisplayName("받은 커서를 해석해 그 뒤부터 조회하고 조회 조건을 그대로 넘긴다")
		void passesDecodedCursorAndFilter() {
			// given
			TicketLedgerCursor cursor = new TicketLedgerCursor(NOW, UUID.randomUUID());
			TicketLedgerFilter filter = TicketLedgerFilter.of(TicketTransactionType.GRANT, null, NOW);
			when(queryRepository.findLedger(USER_ID, filter, cursor, 21)).thenReturn(List.of());
			// when
			service.getMyLedger(USER_ID, filter, new CursorQuery(cursor.encode(), 20));
			// then
			verify(queryRepository).findLedger(USER_ID, filter, cursor, 21);
			verify(queryRepository).countLedger(USER_ID, filter);
		}

		@Test
		@DisplayName("조회 개수가 100을 넘으면 거절한다")
		void rejectsTooLargeSize() {
			// given
			CursorQuery page = new CursorQuery(null, 101);
			// when
			// then
			assertInvalidQuery(() -> service.getMyLedger(USER_ID, TicketLedgerFilter.none(), page));
			verifyNoInteractions(queryRepository);
		}

		@Test
		@DisplayName("조회 개수 100은 허용한다")
		void allowsMaxSize() {
			// given
			when(queryRepository.findLedger(eq(USER_ID), any(), isNull(), anyInt())).thenReturn(List.of());
			// when
			service.getMyLedger(USER_ID, TicketLedgerFilter.none(), new CursorQuery(null, 100));
			// then
			verify(queryRepository).findLedger(eq(USER_ID), any(), isNull(), eq(101));
		}

		@Test
		@DisplayName("시작 시각이 끝 시각보다 앞서지 않으면 거절한다")
		void rejectsEmptyPeriod() {
			// given
			TicketLedgerFilter same = TicketLedgerFilter.of(null, NOW, NOW);
			TicketLedgerFilter reversed = TicketLedgerFilter.of(null, NOW, NOW.minusSeconds(1));
			CursorQuery page = new CursorQuery(null, 20);
			// when
			// then
			assertInvalidQuery(() -> service.getMyLedger(USER_ID, same, page));
			assertInvalidQuery(() -> service.getMyLedger(USER_ID, reversed, page));
			verifyNoInteractions(queryRepository);
		}

		@Test
		@DisplayName("해석할 수 없는 커서는 저장소를 조회하지 않고 거절한다")
		void rejectsMalformedCursor() {
			// given
			CursorQuery page = new CursorQuery("not-a-cursor", 20);
			// when
			// then
			assertInvalidQuery(() -> service.getMyLedger(USER_ID, TicketLedgerFilter.none(), page));
			verifyNoInteractions(queryRepository);
		}

		@Test
		@DisplayName("조회 조건이나 페이지가 없으면 거절한다")
		void rejectsMissingArguments() {
			// given
			// when
			// then
			assertInvalidQuery(() -> service.getMyLedger(USER_ID, null, new CursorQuery(null, 20)));
			assertInvalidQuery(() -> service.getMyLedger(USER_ID, TicketLedgerFilter.none(), null));
			verifyNoInteractions(queryRepository);
		}
	}
}
