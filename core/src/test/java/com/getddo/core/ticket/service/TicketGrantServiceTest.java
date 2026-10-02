package com.getddo.core.ticket.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.ticket.domain.GrantCommand;
import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceClaim;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.TicketLedger;
import com.getddo.core.ticket.domain.TicketLedgerAllocation;
import com.getddo.core.ticket.domain.TicketTransactionType;
import com.getddo.core.ticket.domain.TicketWallet;
import com.getddo.core.ticket.domain.TicketWalletPeriod;
import com.getddo.core.ticket.domain.TicketWalletStatus;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.ticket.repository.GrantSourceRepository;
import com.getddo.core.ticket.repository.TicketLedgerAllocationRepository;
import com.getddo.core.ticket.repository.TicketLedgerRepository;
import com.getddo.core.ticket.repository.TicketWalletRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketGrantServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-15T03:00:00Z");
	private static final TicketWalletPeriod SEPTEMBER = new TicketWalletPeriod(
			LocalDate.parse("2026-09-01"), Instant.parse("2026-09-30T15:00:00Z"));
	private static final UUID USER_ID = UUID.randomUUID();
	private static final GrantSource SOURCE = new GrantSource(GrantSourceType.MISSION, UUID.randomUUID());

	@Mock
	private GrantSourceRepository grantSourceRepository;
	@Mock
	private TicketLedgerRepository ledgerRepository;
	@Mock
	private TicketWalletRepository walletRepository;
	@Mock
	private TicketLedgerAllocationRepository allocationRepository;

	private CountingClock clock;
	private TicketGrantService service;

	@BeforeEach
	void setUp() {
		clock = new CountingClock(NOW);
		service = new TicketGrantService(grantSourceRepository, ledgerRepository, walletRepository,
				allocationRepository, new TimeProvider(clock));
	}

	private static GrantCommand command(long quantity) {
		return new GrantCommand(USER_ID, SOURCE, quantity, "테스트 미션");
	}

	private static TicketWallet wallet(long balance, long version) {
		return new TicketWallet(UUID.randomUUID(), USER_ID, SEPTEMBER.getExpiryMonth(), NOW, SEPTEMBER.getExpiresAt(),
				balance, TicketWalletStatus.ACTIVE, version);
	}

	private static TicketLedger savedGrant(UUID walletId, long quantity, long balanceAfter, long walletVersion) {
		return new TicketLedger(UUID.randomUUID(), walletId, USER_ID, TicketTransactionType.GRANT, quantity,
				SOURCE.idempotencyKey(), "테스트 미션", NOW, balanceAfter, walletVersion, SEPTEMBER.getExpiresAt(),
				SOURCE);
	}

	private void assertErrorCode(Runnable call, TicketErrorCode expected) {
		assertThatThrownBy(call::run)
				.isInstanceOf(TicketException.class)
				.extracting(error -> ((TicketException) error).getErrorCode())
				.isEqualTo(expected);
	}

	@Nested
	@DisplayName("grant 입력 검증")
	class InputValidation {

		@ParameterizedTest
		@ValueSource(longs = {0, -1})
		@DisplayName("수량이 1 미만이면 저장소를 조회하지 않고 거절한다")
		void rejectsNonPositiveQuantity(long quantity) {
			// given
			GrantCommand command = command(quantity);
			// when
			// then
			assertErrorCode(() -> service.grant(command), TicketErrorCode.TICKET_INVALID_GRANT);
			verifyNoInteractions(grantSourceRepository, ledgerRepository, walletRepository, allocationRepository);
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {" ", "\t\n"})
		@DisplayName("사유가 비어 있으면 거절한다")
		void rejectsBlankReason(String reason) {
			// given
			GrantCommand command = new GrantCommand(USER_ID, SOURCE, 1, reason);
			// when
			// then
			assertErrorCode(() -> service.grant(command), TicketErrorCode.TICKET_INVALID_GRANT);
			verifyNoInteractions(grantSourceRepository);
		}

		@Test
		@DisplayName("요청·사용자·청구 종류·청구 ID가 없으면 거절한다")
		void rejectsMissingIdentifiers() {
			// given
			GrantCommand noUser = new GrantCommand(null, SOURCE, 1, "사유");
			GrantCommand noSource = new GrantCommand(USER_ID, null, 1, "사유");
			GrantCommand noType = new GrantCommand(USER_ID, new GrantSource(null, UUID.randomUUID()), 1, "사유");
			GrantCommand noClaim = new GrantCommand(USER_ID, new GrantSource(GrantSourceType.GAME, null), 1, "사유");
			// when
			// then
			assertErrorCode(() -> service.grant(null), TicketErrorCode.TICKET_INVALID_GRANT);
			assertErrorCode(() -> service.grant(noUser), TicketErrorCode.TICKET_INVALID_GRANT);
			assertErrorCode(() -> service.grant(noSource), TicketErrorCode.TICKET_INVALID_GRANT);
			assertErrorCode(() -> service.grant(noType), TicketErrorCode.TICKET_INVALID_GRANT);
			assertErrorCode(() -> service.grant(noClaim), TicketErrorCode.TICKET_INVALID_GRANT);
			verifyNoInteractions(grantSourceRepository);
		}
	}

	@Nested
	@DisplayName("grant 청구 검증")
	class ClaimValidation {

		@Test
		@DisplayName("청구 행이 없으면 거절한다")
		void rejectsMissingClaim() {
			// given
			when(grantSourceRepository.find(SOURCE)).thenReturn(Optional.empty());
			// when
			// then
			assertErrorCode(() -> service.grant(command(1)), TicketErrorCode.TICKET_GRANT_SOURCE_NOT_FOUND);
			verifyNoInteractions(ledgerRepository, walletRepository, allocationRepository);
		}

		@Test
		@DisplayName("청구의 사용자가 요청과 다르면 멱등 조회 전에 거절한다")
		void rejectsOtherUsersClaim() {
			// given
			when(grantSourceRepository.find(SOURCE))
					.thenReturn(Optional.of(new GrantSourceClaim(UUID.randomUUID(), 1)));
			// when
			// then
			assertErrorCode(() -> service.grant(command(1)), TicketErrorCode.TICKET_GRANT_SOURCE_MISMATCH);
			verifyNoInteractions(ledgerRepository, walletRepository, allocationRepository);
		}

		@Test
		@DisplayName("청구의 수량이 요청과 다르면 거절한다")
		void rejectsQuantityMismatch() {
			// given
			when(grantSourceRepository.find(SOURCE)).thenReturn(Optional.of(new GrantSourceClaim(USER_ID, 2)));
			// when
			// then
			assertErrorCode(() -> service.grant(command(1)), TicketErrorCode.TICKET_GRANT_SOURCE_MISMATCH);
			verifyNoInteractions(ledgerRepository, walletRepository, allocationRepository);
		}
	}

	@Nested
	@DisplayName("grant 지급")
	class Grant {

		@BeforeEach
		void validClaim() {
			when(grantSourceRepository.find(SOURCE)).thenReturn(Optional.of(new GrantSourceClaim(USER_ID, 2)));
		}

		@Test
		@DisplayName("이미 지급된 청구면 지갑을 건드리지 않고 기존 결과를 replayed=true로 반환한다")
		void replaysExistingGrant() {
			// given
			TicketLedger existing = savedGrant(UUID.randomUUID(), 2, 5, 3);
			when(ledgerRepository.findByIdempotencyKey(SOURCE.idempotencyKey())).thenReturn(Optional.of(existing));
			// when
			GrantResult result = service.grant(command(2));
			// then
			assertThat(result).isEqualTo(existing.toGrantResult(true));
			verifyNoInteractions(walletRepository, allocationRepository);
			assertThat(clock.reads()).isZero();
		}

		@Test
		@DisplayName("신규 지급은 지급 시각을 한 번만 구해 지갑·원장·배분에 같은 값으로 쓴다")
		void grantsWithSingleGrantedAt() {
			// given
			TicketWallet locked = wallet(3, 4);
			when(ledgerRepository.findByIdempotencyKey(SOURCE.idempotencyKey())).thenReturn(Optional.empty());
			when(walletRepository.getOrCreateForUpdate(USER_ID, SEPTEMBER, NOW)).thenReturn(locked);
			when(ledgerRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
			// when
			GrantResult result = service.grant(command(2));
			// then
			assertThat(clock.reads()).isEqualTo(1);

			ArgumentCaptor<TicketWallet> savedWallet = ArgumentCaptor.forClass(TicketWallet.class);
			verify(walletRepository).save(savedWallet.capture());
			assertThat(savedWallet.getValue()).isEqualTo(locked.deposit(2));

			ArgumentCaptor<TicketLedger> savedLedger = ArgumentCaptor.forClass(TicketLedger.class);
			verify(ledgerRepository).save(savedLedger.capture());
			assertThat(savedLedger.getValue())
					.isEqualTo(TicketLedger.grant(locked.deposit(2), command(2), NOW));

			ArgumentCaptor<TicketLedgerAllocation> savedAllocation =
					ArgumentCaptor.forClass(TicketLedgerAllocation.class);
			verify(allocationRepository).save(savedAllocation.capture());
			assertThat(savedAllocation.getValue().getLedgerId()).isEqualTo(result.getLedgerId());
			assertThat(savedAllocation.getValue().getCreatedAt()).isEqualTo(NOW);

			assertThat(result.getLedgerId()).isNotNull();
			assertThat(result.getWalletId()).isEqualTo(locked.getId());
			assertThat(result.getQuantity()).isEqualTo(2);
			assertThat(result.getBalanceAfter()).isEqualTo(5);
			assertThat(result.getGrantedAt()).isEqualTo(NOW);
			assertThat(result.getExpiresAt()).isEqualTo(SEPTEMBER.getExpiresAt());
			assertThat(result.isReplayed()).isFalse();
		}

		private TicketLedger withId(TicketLedger ledger) {
			return new TicketLedger(UUID.randomUUID(), ledger.getWalletId(), ledger.getUserId(), ledger.getType(),
					ledger.getQuantity(), ledger.getIdempotencyKey(), ledger.getReason(), ledger.getCreatedAt(),
					ledger.getBalanceAfter(), ledger.getWalletVersion(), ledger.getExpiresAt(), ledger.getGrantSource());
		}
	}

	@Nested
	@DisplayName("findGrant")
	class FindGrant {

		@Test
		@DisplayName("지급 원장이 없으면 빈 값을 반환한다")
		void returnsEmptyWhenNotGranted() {
			// given
			when(ledgerRepository.findByIdempotencyKey(SOURCE.idempotencyKey())).thenReturn(Optional.empty());
			// when
			Optional<GrantResult> result = service.findGrant(SOURCE);
			// then
			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("지급 원장이 있으면 replayed=true 결과를 반환하고 청구·지갑은 조회하지 않는다")
		void returnsReplayedResult() {
			// given
			TicketLedger existing = savedGrant(UUID.randomUUID(), 1, 1, 1);
			when(ledgerRepository.findByIdempotencyKey(SOURCE.idempotencyKey())).thenReturn(Optional.of(existing));
			// when
			Optional<GrantResult> result = service.findGrant(SOURCE);
			// then
			assertThat(result).contains(existing.toGrantResult(true));
			verifyNoInteractions(grantSourceRepository, walletRepository, allocationRepository);
		}

		@Test
		@DisplayName("청구 종류나 ID가 없으면 거절한다")
		void rejectsIncompleteSource() {
			// given
			// when
			// then
			assertErrorCode(() -> service.findGrant(null), TicketErrorCode.TICKET_INVALID_GRANT);
			assertErrorCode(() -> service.findGrant(new GrantSource(null, UUID.randomUUID())),
					TicketErrorCode.TICKET_INVALID_GRANT);
			assertErrorCode(() -> service.findGrant(new GrantSource(GrantSourceType.MISSION, null)),
					TicketErrorCode.TICKET_INVALID_GRANT);
			verifyNoInteractions(ledgerRepository);
		}
	}

	/** 읽은 횟수를 세는 고정 시계. */
	private static final class CountingClock extends Clock {

		private final Instant instant;
		private final AtomicInteger reads = new AtomicInteger();

		CountingClock(Instant instant) {
			this.instant = instant;
		}

		int reads() {
			return reads.get();
		}

		@Override
		public Instant instant() {
			reads.incrementAndGet();
			return instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			throw new UnsupportedOperationException();
		}
	}
}
