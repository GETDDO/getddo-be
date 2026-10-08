package com.getddo.db.ticket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.common.pagination.CursorQuery;
import com.getddo.core.common.pagination.CursorResult;
import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.MyTicketWallets;
import com.getddo.core.ticket.domain.TicketLedgerFilter;
import com.getddo.core.ticket.domain.TicketTransactionView;
import com.getddo.core.ticket.domain.TicketWalletStatus;
import com.getddo.core.ticket.domain.TicketWalletView;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.ticket.service.TicketQueryService;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** GD-46 테스트 방법(지급 후 잔액, 만료 지갑 제외, 커서 이동 안정성)을 실제 MySQL에서 검증한다. */
class TicketQueryServiceIntegrationTest extends TicketIntegrationTestSupport {

	private static final Instant SEPTEMBER = Instant.parse("2026-09-15T03:00:00Z");
	/** 10월 1일 00:30 KST. 9월 지급분이 막 만료된 시각이다. */
	private static final Instant OCTOBER_FIRST = Instant.parse("2026-09-30T15:30:00Z");

	@Autowired
	private TicketQueryService queryService;

	@Test
	@DisplayName("지급 후 사용 가능 잔액과 지갑 잔액이 원장 합계와 같다")
	void balanceMatchesLedgerAfterGrants() {
		// given
		clock.set(SEPTEMBER);
		GrantResult first = grantNewMissionClaim(userId, 2);
		grantNewMissionClaim(userId, 3);
		// when
		MyTicketWallets result = queryService.getMyWallets(userId);
		// then
		long ledgerSum = count("select sum(quantity) from ticket_ledger where user_id = ?", bytes(userId));
		assertThat(result.getAvailableBalance()).isEqualTo(ledgerSum).isEqualTo(5);
		assertThat(result.getServerTime()).isEqualTo(SEPTEMBER);
		assertThat(result.getWallets()).singleElement().satisfies(wallet -> {
			assertThat(wallet.getId()).isEqualTo(first.getWalletId());
			assertThat(wallet.getBalance()).isEqualTo(5);
			assertThat(wallet.getStatus()).isEqualTo(TicketWalletStatus.ACTIVE);
			assertThat(wallet.getExpiresAt()).isEqualTo(first.getExpiresAt());
			assertThat(wallet.getValidFrom()).isEqualTo(first.getGrantedAt());
		});
	}

	@Test
	@DisplayName("만료 처리 전이라도 만료 시각이 지난 지갑과 만료 처리된 지갑은 사용 가능 잔액에서 빠진다")
	void excludesExpiredWallets() {
		// given: 8월 지갑은 만료 처리됨, 9월 지갑은 만료 시각이 지났지만 아직 ACTIVE로 저장됨
		UUID august = insertWallet(userId, "2026-08-01", "2026-08-31T15:00:00Z", 4, TicketWalletStatus.EXPIRED);
		clock.set(SEPTEMBER);
		GrantResult september = grantNewMissionClaim(userId, 2);
		clock.set(OCTOBER_FIRST);
		GrantResult october = grantNewMissionClaim(userId, 1);
		// when
		MyTicketWallets result = queryService.getMyWallets(userId);
		// then
		assertThat(result.getAvailableBalance()).isEqualTo(1);
		assertThat(result.getServerTime()).isEqualTo(OCTOBER_FIRST);
		assertThat(result.getWallets()).extracting(TicketWalletView::getId)
				.containsExactly(october.getWalletId(), september.getWalletId(), august);
		assertThat(result.getWallets()).extracting(TicketWalletView::getStatus).containsExactly(
				TicketWalletStatus.ACTIVE, TicketWalletStatus.EXPIRED, TicketWalletStatus.EXPIRED);
		assertThat(jdbc.queryForObject("select status from ticket_wallets where id = ?", String.class,
				bytes(september.getWalletId()))).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("다음 커서를 따라가면 전체 수만큼 중복 없이 최신순으로 받고 마지막에 커서가 없다")
	void followsNextCursorToTheEnd() {
		// given
		clock.set(SEPTEMBER);
		for (int i = 0; i < 5; i++) {
			grantNewMissionClaim(userId, 1);
		}
		// when
		List<TicketTransactionView> received = new ArrayList<>();
		List<Long> totals = new ArrayList<>();
		String cursor = null;
		int pages = 0;
		do {
			CursorResult<TicketTransactionView> page =
					queryService.getMyLedger(userId, TicketLedgerFilter.none(), new CursorQuery(cursor, 2));
			received.addAll(page.getItems());
			totals.add(page.getTotalElements());
			cursor = page.getNextCursor();
			pages++;
		} while (cursor != null && pages < 10);
		// then
		assertThat(pages).isEqualTo(3);
		assertThat(totals).containsOnly(5L);
		assertThat(received).hasSize(5).extracting(TicketTransactionView::getId).doesNotHaveDuplicates();
		assertThat(received).extracting(TicketTransactionView::getCreatedAt)
				.isSortedAccordingTo((a, b) -> b.compareTo(a));
	}

	@Test
	@DisplayName("해석할 수 없는 커서는 TICKET-004로 거절한다")
	void rejectsMalformedCursor() {
		// given
		CursorQuery page = new CursorQuery("broken", 20);
		// when
		// then
		assertThatThrownBy(() -> queryService.getMyLedger(userId, TicketLedgerFilter.none(), page))
				.isInstanceOf(TicketException.class)
				.extracting(error -> ((TicketException) error).getErrorCode())
				.isEqualTo(TicketErrorCode.TICKET_INVALID_LEDGER_QUERY);
	}
}
