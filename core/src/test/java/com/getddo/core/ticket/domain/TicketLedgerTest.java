package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TicketLedgerTest {

	private static final Instant GRANTED_AT = Instant.parse("2026-09-15T03:00:00Z");
	private static final UUID USER_ID = UUID.randomUUID();
	private static final GrantSource SOURCE = new GrantSource(GrantSourceType.GAME, UUID.randomUUID());

	private final TicketWallet deposited = new TicketWallet(UUID.randomUUID(), USER_ID,
			LocalDate.parse("2026-09-01"), GRANTED_AT, Instant.parse("2026-09-30T15:00:00Z"),
			4, TicketWalletStatus.ACTIVE, 3);

	@Test
	@DisplayName("지급 원장은 갱신된 지갑의 잔액·version·만료 시각과 지급 시각을 그대로 기록한다")
	void grantRecordsDepositedWalletState() {
		// given
		GrantCommand command = new GrantCommand(USER_ID, SOURCE, 1, "게임 보상");
		// when
		TicketLedger ledger = TicketLedger.grant(deposited, command, GRANTED_AT);
		// then
		assertThat(ledger.getId()).isNull();
		assertThat(ledger.getWalletId()).isEqualTo(deposited.getId());
		assertThat(ledger.getUserId()).isEqualTo(USER_ID);
		assertThat(ledger.getType()).isEqualTo(TicketTransactionType.GRANT);
		assertThat(ledger.getQuantity()).isEqualTo(1);
		assertThat(ledger.getIdempotencyKey()).isEqualTo(SOURCE.idempotencyKey());
		assertThat(ledger.getReason()).isEqualTo("게임 보상");
		assertThat(ledger.getCreatedAt()).isEqualTo(GRANTED_AT);
		assertThat(ledger.getBalanceAfter()).isEqualTo(4);
		assertThat(ledger.getWalletVersion()).isEqualTo(3);
		assertThat(ledger.getExpiresAt()).isEqualTo(deposited.getExpiresAt());
		assertThat(ledger.getGrantSource()).isEqualTo(SOURCE);
	}

	@Test
	@DisplayName("지급 원장의 결과는 원장 생성 시각을 지급 시각으로 쓴다")
	void convertsToGrantResult() {
		// given
		UUID ledgerId = UUID.randomUUID();
		TicketLedger saved = new TicketLedger(ledgerId, deposited.getId(), USER_ID, TicketTransactionType.GRANT, 1,
				SOURCE.idempotencyKey(), "게임 보상", GRANTED_AT, 4, 3, deposited.getExpiresAt(), SOURCE);
		// when
		GrantResult result = saved.toGrantResult(true);
		// then
		assertThat(result).isEqualTo(new GrantResult(ledgerId, deposited.getId(), 1, 4, GRANTED_AT,
				deposited.getExpiresAt(), true));
	}

	@Test
	@DisplayName("자기 입금 배분 행은 세 ID가 모두 이 지급이고 수량·생성 시각이 원장과 같다")
	void selfCreditAllocation() {
		// given
		UUID ledgerId = UUID.randomUUID();
		TicketLedger saved = new TicketLedger(ledgerId, deposited.getId(), USER_ID, TicketTransactionType.GRANT, 2,
				SOURCE.idempotencyKey(), "게임 보상", GRANTED_AT, 4, 3, deposited.getExpiresAt(), SOURCE);
		// when
		TicketLedgerAllocation allocation = TicketLedgerAllocation.selfCredit(saved);
		// then
		assertThat(allocation).isEqualTo(new TicketLedgerAllocation(ledgerId, ledgerId, ledgerId, 2, GRANTED_AT));
	}

	@Test
	@DisplayName("저장 전 원장으로는 배분 행을 만들 수 없다")
	void selfCreditRequiresSavedLedger() {
		// given
		TicketLedger unsaved = TicketLedger.grant(deposited, new GrantCommand(USER_ID, SOURCE, 1, "게임 보상"),
				GRANTED_AT);
		// when
		// then
		assertThatNullPointerException().isThrownBy(() -> TicketLedgerAllocation.selfCredit(unsaved));
	}
}
