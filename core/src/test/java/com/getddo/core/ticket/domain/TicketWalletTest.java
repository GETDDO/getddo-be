package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class TicketWalletTest {

	private static TicketWallet wallet(long balance, TicketWalletStatus status, long version) {
		return new TicketWallet(UUID.randomUUID(), UUID.randomUUID(), LocalDate.parse("2026-09-01"),
				Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-30T15:00:00Z"),
				balance, status, version);
	}

	@Test
	@DisplayName("입금하면 잔액이 수량만큼, version이 1 늘어난 새 지갑을 반환하고 원래 지갑은 그대로다")
	void depositReturnsNewWallet() {
		// given
		TicketWallet original = wallet(5, TicketWalletStatus.ACTIVE, 2);
		// when
		TicketWallet deposited = original.deposit(3);
		// then
		assertThat(deposited.balance()).isEqualTo(8);
		assertThat(deposited.version()).isEqualTo(3);
		assertThat(deposited.id()).isEqualTo(original.id());
		assertThat(original.balance()).isEqualTo(5);
		assertThat(original.version()).isEqualTo(2);
	}

	@ParameterizedTest
	@ValueSource(longs = {0, -1})
	@DisplayName("입금 수량이 1 미만이면 거절한다")
	void rejectsNonPositiveDeposit(long quantity) {
		// given
		TicketWallet wallet = wallet(0, TicketWalletStatus.ACTIVE, 0);
		// when
		// then
		assertThatIllegalArgumentException().isThrownBy(() -> wallet.deposit(quantity));
	}

	@Test
	@DisplayName("만료된 지갑에는 입금할 수 없다")
	void rejectsDepositToExpiredWallet() {
		// given
		TicketWallet wallet = wallet(0, TicketWalletStatus.EXPIRED, 4);
		// when
		// then
		assertThatIllegalStateException().isThrownBy(() -> wallet.deposit(1));
	}
}
